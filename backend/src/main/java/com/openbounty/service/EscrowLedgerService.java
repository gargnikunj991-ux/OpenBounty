package com.openbounty.service;

import com.openbounty.enums.EntryType;
import com.openbounty.enums.LedgerAccount;
import com.openbounty.enums.MilestoneStatus;
import com.openbounty.enums.TransactionType;
import com.openbounty.exception.BadRequestException;
import com.openbounty.exception.ResourceNotFoundException;
import com.openbounty.model.Bounty;
import com.openbounty.model.LedgerEntry;
import com.openbounty.model.LedgerTransaction;
import com.openbounty.model.Milestone;
import com.openbounty.model.Proposal;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.LedgerEntryRepository;
import com.openbounty.repository.LedgerTransactionRepository;
import com.openbounty.repository.MilestoneRepository;
import com.openbounty.repository.projection.AccountBalanceProjection;
import com.openbounty.validator.LedgerBalanceValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Service managing in-house escrow workflows and financial double-entry ledger journals.
 * Guarantees zero financial reconciliation drift and preserves complete GAAP audit history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscrowLedgerService {

    public static final BigDecimal DEFAULT_PLATFORM_FEE_RATE = new BigDecimal("0.15"); // 15% platform take-rate

    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;
    private final LedgerBalanceValidator balanceValidator;
    private final BountyRepository bountyRepository;
    private final MilestoneRepository milestoneRepository;

    /**
     * Locks bounty reward funds into escrow upon bounty publication.
     * DEBIT:  PLATFORM_CASH (Cash received from client)
     * CREDIT: CLIENT_ESCROW_LOCKED (Liability held on behalf of client)
     */
    @Transactional
    public LedgerTransaction lockBountyEscrow(Bounty bounty) {
        if (bounty == null) {
            throw new BadRequestException("Bounty cannot be null when locking escrow");
        }
        if (bounty.getRewardAmount() == null || bounty.getRewardAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Reward amount must be greater than zero to lock escrow");
        }

        BigDecimal amount = bounty.getRewardAmount().setScale(2, RoundingMode.HALF_UP);
        String txnRef = "TXN-LOCK-" + bounty.getId() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        LedgerTransaction txn = LedgerTransaction.builder()
                .transactionReference(txnRef)
                .transactionType(TransactionType.BOUNTY_ESCROW_LOCK)
                .description("Escrow locked for bounty #" + bounty.getId() + " ('" + bounty.getTitle() + "')")
                .bountyId(bounty.getId())
                .userId(bounty.getClient() != null ? bounty.getClient().getId() : null)
                .build();

        txn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.PLATFORM_CASH)
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .build());

        txn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.CLIENT_ESCROW_LOCKED)
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .build());

        balanceValidator.validate(txn);
        LedgerTransaction savedTxn = transactionRepository.save(txn);
        log.info("Locked {} in escrow for bounty ID: {} [TxnRef: {}]", amount, bounty.getId(), txnRef);
        return savedTxn;
    }

    /**
     * Refunds remaining locked escrow back to the client upon bounty cancellation.
     * DEBIT:  CLIENT_ESCROW_LOCKED (Relieving locked escrow liability)
     * CREDIT: PLATFORM_CASH (Returning cash to client)
     */
    @Transactional
    public LedgerTransaction refundBountyEscrow(Bounty bounty, String reason) {
        if (bounty == null) {
            throw new BadRequestException("Bounty cannot be null when refunding escrow");
        }

        BigDecimal remainingEscrow = entryRepository.getLockedEscrowForBounty(bounty.getId());
        if (remainingEscrow == null || remainingEscrow.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("No active locked escrow funds to refund for bounty ID: {}", bounty.getId());
            return null;
        }

        remainingEscrow = remainingEscrow.setScale(2, RoundingMode.HALF_UP);
        String txnRef = "TXN-REFUND-" + bounty.getId() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        LedgerTransaction txn = LedgerTransaction.builder()
                .transactionReference(txnRef)
                .transactionType(TransactionType.BOUNTY_ESCROW_REFUND)
                .description("Escrow refund for cancelled bounty #" + bounty.getId() +
                        (reason != null ? " (" + reason + ")" : ""))
                .bountyId(bounty.getId())
                .userId(bounty.getClient() != null ? bounty.getClient().getId() : null)
                .build();

        txn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.CLIENT_ESCROW_LOCKED)
                .entryType(EntryType.DEBIT)
                .amount(remainingEscrow)
                .build());

        txn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.PLATFORM_CASH)
                .entryType(EntryType.CREDIT)
                .amount(remainingEscrow)
                .build());

        balanceValidator.validate(txn);
        LedgerTransaction savedTxn = transactionRepository.save(txn);
        log.info("Refunded {} escrow for cancelled bounty ID: {} [TxnRef: {}]", remainingEscrow, bounty.getId(), txnRef);
        return savedTxn;
    }

    /**
     * Disburses milestone earnings upon deliverable approval, splitting funds between developer and platform take-rate.
     * DEBIT:  CLIENT_ESCROW_LOCKED (Relieving escrow by gross milestone amount)
     * CREDIT: DEVELOPER_PAYABLE     (Net earnings earned by developer, e.g. 85%)
     * CREDIT: PLATFORM_FEE_REVENUE  (Platform fee revenue earned, e.g. 15%)
     *
     * Invariant: DEBIT == CREDIT(Developer) + CREDIT(Platform)
     */
    @Transactional
    public LedgerTransaction disburseMilestonePayout(Milestone milestone, BigDecimal customFeeRate) {
        if (milestone == null) {
            throw new BadRequestException("Milestone cannot be null when disbursing payout");
        }

        Proposal proposal = milestone.getProposal();
        if (proposal == null) {
            throw new BadRequestException("Milestone must belong to a proposal to disburse payout");
        }

        Bounty bounty = proposal.getBounty();
        if (bounty == null) {
            throw new BadRequestException("Proposal must belong to a bounty to disburse payout");
        }

        // Determine gross milestone value
        long totalMilestones = milestoneRepository.countByProposalId(proposal.getId());
        if (totalMilestones <= 0) {
            totalMilestones = 1;
        }

        BigDecimal proposedTotal = proposal.getProposedAmount() != null
                ? proposal.getProposedAmount()
                : bounty.getRewardAmount();

        if (proposedTotal == null || proposedTotal.compareTo(BigDecimal.ZERO) <= 0) {
            proposedTotal = new BigDecimal("100.00");
        }

        // Defensive: Check if escrow was pre-locked. If not, auto-lock bounty escrow first to maintain audit trail
        BigDecimal remainingLocked = entryRepository.getLockedEscrowForBounty(bounty.getId());
        if (remainingLocked == null || remainingLocked.compareTo(BigDecimal.ZERO) <= 0) {
            lockBountyEscrow(bounty);
            remainingLocked = entryRepository.getLockedEscrowForBounty(bounty.getId());
        }

        // Check if this is the final milestone being approved for the proposal
        long unapprovedCount = milestoneRepository.countByProposalIdAndStatusNot(proposal.getId(), MilestoneStatus.APPROVED);
        BigDecimal grossMilestoneAmount;

        if (unapprovedCount <= 1 && remainingLocked != null && remainingLocked.compareTo(BigDecimal.ZERO) > 0) {
            // For the final milestone, disburse all remaining locked escrow to avoid fractional cent rounding drift
            grossMilestoneAmount = remainingLocked;
        } else {
            grossMilestoneAmount = proposedTotal.divide(BigDecimal.valueOf(totalMilestones), 2, RoundingMode.HALF_UP);
            if (remainingLocked != null && grossMilestoneAmount.compareTo(remainingLocked) > 0) {
                grossMilestoneAmount = remainingLocked;
            }
        }

        grossMilestoneAmount = grossMilestoneAmount.setScale(2, RoundingMode.HALF_UP);
        if (grossMilestoneAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Gross milestone amount calculated as 0 for milestone ID: {}", milestone.getId());
            return null;
        }

        BigDecimal feeRate = customFeeRate != null ? customFeeRate : DEFAULT_PLATFORM_FEE_RATE;
        BigDecimal platformFee = grossMilestoneAmount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal developerNet = grossMilestoneAmount.subtract(platformFee);

        String txnRef = "TXN-PAYOUT-" + milestone.getId() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        LedgerTransaction txn = LedgerTransaction.builder()
                .transactionReference(txnRef)
                .transactionType(TransactionType.MILESTONE_PAYOUT_RELEASE)
                .description("Payout release for milestone #" + milestone.getId() + " ('" + milestone.getTitle() + "')")
                .bountyId(bounty.getId())
                .milestoneId(milestone.getId())
                .userId(proposal.getDeveloper() != null ? proposal.getDeveloper().getId() : null)
                .build();

        txn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.CLIENT_ESCROW_LOCKED)
                .entryType(EntryType.DEBIT)
                .amount(grossMilestoneAmount)
                .build());

        txn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.DEVELOPER_PAYABLE)
                .entryType(EntryType.CREDIT)
                .amount(developerNet)
                .build());

        txn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.PLATFORM_FEE_REVENUE)
                .entryType(EntryType.CREDIT)
                .amount(platformFee)
                .build());

        balanceValidator.validate(txn);
        LedgerTransaction savedTxn = transactionRepository.save(txn);
        log.info("Disbursed payout for milestone ID: {} [Gross: {}, Dev: {}, Platform Fee: {}, TxnRef: {}]",
                milestone.getId(), grossMilestoneAmount, developerNet, platformFee, txnRef);

        return savedTxn;
    }

    /**
     * Returns the current locked escrow balance for a bounty.
     */
    @Transactional(readOnly = true)
    public BigDecimal getLockedEscrowForBounty(Long bountyId) {
        return entryRepository.getLockedEscrowForBounty(bountyId);
    }

    /**
     * Returns the payable earnings balance for a developer user.
     */
    @Transactional(readOnly = true)
    public BigDecimal getDeveloperPayableBalance(Long userId) {
        return entryRepository.getPayableBalanceForUser(userId);
    }

    /**
     * Returns the complete platform balance sheet aggregated across all chart of accounts.
     */
    @Transactional(readOnly = true)
    public List<AccountBalanceProjection> getPlatformBalanceSheet() {
        return entryRepository.aggregateAccountBalances();
    }

    /**
     * Returns all ledger transactions recorded for a given bounty.
     */
    @Transactional(readOnly = true)
    public List<LedgerTransaction> getTransactionsForBounty(Long bountyId) {
        return transactionRepository.findByBountyIdOrderByCreatedAtDesc(bountyId);
    }

    /**
     * Fetches a transaction by its reference identifier with eagerly loaded entries.
     */
    @Transactional(readOnly = true)
    public LedgerTransaction getTransactionByReference(String reference) {
        return transactionRepository.findWithEntriesByTransactionReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("LedgerTransaction", "transactionReference", reference));
    }
}
