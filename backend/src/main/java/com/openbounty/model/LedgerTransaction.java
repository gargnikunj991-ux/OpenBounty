package com.openbounty.model;

import com.openbounty.enums.EntryType;
import com.openbounty.enums.TransactionType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * An immutable financial journal transaction containing at least two balanced LedgerEntry records.
 * Follows standard double-entry bookkeeping: SUM(Debits) == SUM(Credits).
 */
@Entity
@Table(
    name = "ledger_transactions",
    indexes = {
        @Index(name = "idx_ledger_txn_ref", columnList = "transaction_reference", unique = true),
        @Index(name = "idx_ledger_txn_bounty", columnList = "bounty_id"),
        @Index(name = "idx_ledger_txn_milestone", columnList = "milestone_id"),
        @Index(name = "idx_ledger_txn_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "entries")
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_reference", nullable = false, unique = true, length = 64)
    private String transactionReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 40)
    private TransactionType transactionType;

    @Column(length = 255)
    private String description;

    @Column(name = "bounty_id")
    private Long bountyId;

    @Column(name = "milestone_id")
    private Long milestoneId;

    @Column(name = "user_id")
    private Long userId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LedgerEntry> entries = new ArrayList<>();

    /**
     * Relationship synchronization helper ensuring the bidirectional mapping is consistent.
     */
    public void addEntry(LedgerEntry entry) {
        if (entries == null) {
            entries = new ArrayList<>();
        }
        entries.add(entry);
        entry.setTransaction(this);
    }

    /**
     * Calculates the sum of all DEBIT entries in this transaction.
     */
    public BigDecimal calculateTotalDebit() {
        if (entries == null || entries.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return entries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT && e.getAmount() != null)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculates the sum of all CREDIT entries in this transaction.
     */
    public BigDecimal calculateTotalCredit() {
        if (entries == null || entries.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return entries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT && e.getAmount() != null)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Evaluates whether this transaction satisfies the double-entry accounting invariant.
     */
    public boolean isBalanced() {
        if (entries == null || entries.size() < 2) {
            return false;
        }
        return calculateTotalDebit().compareTo(calculateTotalCredit()) == 0;
    }
}
