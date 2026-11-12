package com.openbounty.validator;

import com.openbounty.enums.EntryType;
import com.openbounty.exception.BadRequestException;
import com.openbounty.exception.UnbalancedLedgerException;
import com.openbounty.model.LedgerEntry;
import com.openbounty.model.LedgerTransaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Strict double-entry invariant validator guaranteeing that every financial transaction
 * adheres to GAAP double-entry balancing rules before being committed to the database.
 */
@Component
public class LedgerBalanceValidator {

    /**
     * Validates that the transaction has valid entries and that total debits exactly equal total credits.
     *
     * @param transaction the ledger transaction to validate
     * @throws UnbalancedLedgerException if debits do not match credits
     * @throws BadRequestException if entries are malformed or non-positive
     */
    public void validate(LedgerTransaction transaction) {
        if (transaction == null) {
            throw new BadRequestException("Ledger transaction cannot be null");
        }

        List<LedgerEntry> entries = transaction.getEntries();
        if (entries == null || entries.size() < 2) {
            throw new BadRequestException("A valid double-entry ledger transaction must contain at least 2 entries (debit and credit)");
        }

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (LedgerEntry entry : entries) {
            if (entry.getAccount() == null) {
                throw new BadRequestException("Ledger entry account cannot be null");
            }
            if (entry.getEntryType() == null) {
                throw new BadRequestException("Ledger entry type (DEBIT/CREDIT) cannot be null");
            }
            if (entry.getAmount() == null || entry.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Ledger entry amount must be strictly greater than zero");
            }

            if (entry.getEntryType() == EntryType.DEBIT) {
                totalDebit = totalDebit.add(entry.getAmount());
            } else if (entry.getEntryType() == EntryType.CREDIT) {
                totalCredit = totalCredit.add(entry.getAmount());
            }
        }

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new UnbalancedLedgerException(totalDebit, totalCredit);
        }
    }
}
