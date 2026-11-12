package com.openbounty.repository;

import com.openbounty.enums.LedgerAccount;
import com.openbounty.model.LedgerEntry;
import com.openbounty.repository.projection.AccountBalanceProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByTransactionId(Long transactionId);

    @Query("SELECT e.account AS account, " +
           "SUM(CASE WHEN e.entryType = com.openbounty.enums.EntryType.DEBIT THEN e.amount ELSE 0.0 END) AS totalDebit, " +
           "SUM(CASE WHEN e.entryType = com.openbounty.enums.EntryType.CREDIT THEN e.amount ELSE 0.0 END) AS totalCredit " +
           "FROM LedgerEntry e GROUP BY e.account")
    List<AccountBalanceProjection> aggregateAccountBalances();

    @Query("SELECT e.account AS account, " +
           "SUM(CASE WHEN e.entryType = com.openbounty.enums.EntryType.DEBIT THEN e.amount ELSE 0.0 END) AS totalDebit, " +
           "SUM(CASE WHEN e.entryType = com.openbounty.enums.EntryType.CREDIT THEN e.amount ELSE 0.0 END) AS totalCredit " +
           "FROM LedgerEntry e WHERE e.account = :account GROUP BY e.account")
    Optional<AccountBalanceProjection> aggregateBalanceForAccount(@Param("account") LedgerAccount account);

    @Query("SELECT COALESCE(SUM(CASE WHEN e.entryType = com.openbounty.enums.EntryType.CREDIT THEN e.amount ELSE -e.amount END), 0) " +
           "FROM LedgerEntry e WHERE e.account = com.openbounty.enums.LedgerAccount.CLIENT_ESCROW_LOCKED " +
           "AND e.transaction.bountyId = :bountyId")
    BigDecimal getLockedEscrowForBounty(@Param("bountyId") Long bountyId);

    @Query("SELECT COALESCE(SUM(CASE WHEN e.entryType = com.openbounty.enums.EntryType.CREDIT THEN e.amount ELSE -e.amount END), 0) " +
           "FROM LedgerEntry e WHERE e.account = com.openbounty.enums.LedgerAccount.DEVELOPER_PAYABLE " +
           "AND e.transaction.userId = :userId")
    BigDecimal getPayableBalanceForUser(@Param("userId") Long userId);
}
