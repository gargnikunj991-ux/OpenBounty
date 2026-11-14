package com.openbounty.controller;

import com.openbounty.dto.response.common.PagedResponse;
import com.openbounty.dto.response.ledger.BountyEscrowSummaryResponse;
import com.openbounty.dto.response.ledger.LedgerTransactionResponse;
import com.openbounty.dto.response.ledger.PlatformBalanceSheetResponse;
import com.openbounty.enums.LedgerAccount;
import com.openbounty.enums.Role;
import com.openbounty.enums.TransactionType;
import com.openbounty.exception.AccessDeniedException;
import com.openbounty.exception.ResourceNotFoundException;
import com.openbounty.model.Bounty;
import com.openbounty.model.LedgerTransaction;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.LedgerTransactionRepository;
import com.openbounty.repository.projection.AccountBalanceProjection;
import com.openbounty.security.UserPrincipal;
import com.openbounty.service.EscrowLedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
@Tag(name = "Ledger & Escrow", description = "Double-entry bookkeeping and escrow balance audit APIs")
public class LedgerController {

    private final EscrowLedgerService escrowLedgerService;
    private final LedgerTransactionRepository transactionRepository;
    private final BountyRepository bountyRepository;

    @GetMapping("/balance-sheet")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get platform balance sheet", description = "Aggregates all chart of accounts into an audit balance sheet (Admin only).")
    public ResponseEntity<PlatformBalanceSheetResponse> getPlatformBalanceSheet() {
        log.info("Admin generating platform balance sheet audit report");
        List<AccountBalanceProjection> projections = escrowLedgerService.getPlatformBalanceSheet();

        BigDecimal cash = getNetBalance(projections, LedgerAccount.PLATFORM_CASH);
        BigDecimal escrow = getNetBalance(projections, LedgerAccount.CLIENT_ESCROW_LOCKED);
        BigDecimal payable = getNetBalance(projections, LedgerAccount.DEVELOPER_PAYABLE);
        BigDecimal fee = getNetBalance(projections, LedgerAccount.PLATFORM_FEE_REVENUE);
        BigDecimal suspense = getNetBalance(projections, LedgerAccount.DISPUTE_SUSPENSE);

        // Assets (Cash) == Liabilities (Escrow + Developer Payable + Suspense) + Revenue (Fee)
        boolean isBalanced = cash.compareTo(escrow.add(payable).add(suspense).add(fee)) == 0;

        PlatformBalanceSheetResponse response = PlatformBalanceSheetResponse.builder()
                .platformCash(cash)
                .clientEscrowLocked(escrow)
                .developerPayable(payable)
                .platformFeeRevenue(fee)
                .disputeSuspense(suspense)
                .balanced(isBalanced)
                .generatedAt(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get paginated transaction audit trail", description = "Retrieves paginated double-entry ledger transactions (Admin only).")
    public ResponseEntity<PagedResponse<LedgerTransactionResponse>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<LedgerTransaction> transactionPage = transactionRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        return ResponseEntity.ok(PagedResponse.from(transactionPage, LedgerTransactionResponse::from));
    }

    @GetMapping("/transactions/{reference}")
    @PreAuthorize("hasAnyRole('CLIENT', 'DEVELOPER', 'ADMIN')")
    @Operation(summary = "Get ledger transaction by reference", description = "Retrieves transaction details by transaction reference identifier.")
    public ResponseEntity<LedgerTransactionResponse> getTransactionByReference(
            @PathVariable String reference,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        LedgerTransaction txn = escrowLedgerService.getTransactionByReference(reference);

        boolean isAdmin = currentUser.getRole() == Role.ROLE_ADMIN;
        boolean isInitiator = txn.getUserId() != null && txn.getUserId().equals(currentUser.getId());

        if (!isAdmin && !isInitiator) {
            // Also permit if user is client or assigned developer on associated bounty
            if (txn.getBountyId() != null) {
                Bounty bounty = bountyRepository.findById(txn.getBountyId()).orElse(null);
                boolean isParticipant = bounty != null && (
                        (bounty.getClient() != null && bounty.getClient().getId().equals(currentUser.getId())) ||
                        (bounty.getAssignedDeveloper() != null && bounty.getAssignedDeveloper().getId().equals(currentUser.getId()))
                );
                if (!isParticipant) {
                    throw new AccessDeniedException("You do not have permission to view this financial transaction.");
                }
            } else {
                throw new AccessDeniedException("You do not have permission to view this financial transaction.");
            }
        }

        return ResponseEntity.ok(LedgerTransactionResponse.from(txn));
    }

    @GetMapping("/escrow/bounties/{bountyId}")
    @PreAuthorize("hasAnyRole('CLIENT', 'DEVELOPER', 'ADMIN')")
    @Operation(summary = "Get bounty escrow summary", description = "Audits escrow funding, disbursement, and refunds for a bounty.")
    public ResponseEntity<BountyEscrowSummaryResponse> getBountyEscrowSummary(
            @PathVariable Long bountyId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        Bounty bounty = bountyRepository.findById(bountyId)
                .orElseThrow(() -> new ResourceNotFoundException("Bounty", "id", bountyId));

        boolean isAdmin = currentUser.getRole() == Role.ROLE_ADMIN;
        boolean isClient = bounty.getClient() != null && bounty.getClient().getId().equals(currentUser.getId());
        boolean isDev = bounty.getAssignedDeveloper() != null && bounty.getAssignedDeveloper().getId().equals(currentUser.getId());

        if (!isAdmin && !isClient && !isDev) {
            log.warn("Unauthorized escrow inquiry attempt for bounty ID {} by user ID {}", bountyId, currentUser.getId());
            throw new AccessDeniedException("You are not authorized to view financial escrow data for this bounty.");
        }

        BigDecimal lockedEscrow = escrowLedgerService.getLockedEscrowForBounty(bountyId);
        List<LedgerTransaction> transactions = escrowLedgerService.getTransactionsForBounty(bountyId);

        BigDecimal disbursed = transactions.stream()
                .filter(t -> t.getTransactionType() == TransactionType.MILESTONE_PAYOUT_RELEASE)
                .map(LedgerTransaction::calculateTotalDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal refunded = transactions.stream()
                .filter(t -> t.getTransactionType() == TransactionType.BOUNTY_ESCROW_REFUND)
                .map(LedgerTransaction::calculateTotalDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<LedgerTransactionResponse> transactionResponses = transactions.stream()
                .map(LedgerTransactionResponse::from)
                .toList();

        return ResponseEntity.ok(BountyEscrowSummaryResponse.builder()
                .bountyId(bounty.getId())
                .bountyTitle(bounty.getTitle())
                .totalRewardAmount(bounty.getRewardAmount())
                .currentLockedEscrow(lockedEscrow)
                .totalDisbursed(disbursed)
                .totalRefunded(refunded)
                .transactions(transactionResponses)
                .build());
    }

    @GetMapping("/payable/me")
    @PreAuthorize("hasRole('DEVELOPER')")
    @Operation(summary = "Get authenticated developer payable balance", description = "Returns the accumulated payable earnings for the developer.")
    public ResponseEntity<Map<String, Object>> getMyPayableBalance(@AuthenticationPrincipal UserPrincipal currentUser) {
        BigDecimal balance = escrowLedgerService.getDeveloperPayableBalance(currentUser.getId());
        return ResponseEntity.ok(Map.of(
                "userId", currentUser.getId(),
                "payableBalance", balance != null ? balance : BigDecimal.ZERO
        ));
    }

    private BigDecimal getNetBalance(List<AccountBalanceProjection> projections, LedgerAccount account) {
        if (projections == null) return BigDecimal.ZERO;
        return projections.stream()
                .filter(p -> p.getAccount() == account)
                .findFirst()
                .map(AccountBalanceProjection::getNetBalance)
                .orElse(BigDecimal.ZERO);
    }
}
