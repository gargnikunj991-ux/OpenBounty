package com.openbounty.dto.response.ledger;

import com.openbounty.enums.TransactionType;
import com.openbounty.model.LedgerTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerTransactionResponse {

    private Long id;
    private String transactionReference;
    private TransactionType transactionType;
    private String description;
    private Long bountyId;
    private Long milestoneId;
    private Long userId;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private boolean balanced;
    private LocalDateTime createdAt;
    private List<LedgerEntryResponse> entries;

    public static LedgerTransactionResponse from(LedgerTransaction txn) {
        if (txn == null) return null;
        List<LedgerEntryResponse> entryResponses = txn.getEntries() != null
                ? txn.getEntries().stream().map(LedgerEntryResponse::from).toList()
                : Collections.emptyList();

        return LedgerTransactionResponse.builder()
                .id(txn.getId())
                .transactionReference(txn.getTransactionReference())
                .transactionType(txn.getTransactionType())
                .description(txn.getDescription())
                .bountyId(txn.getBountyId())
                .milestoneId(txn.getMilestoneId())
                .userId(txn.getUserId())
                .totalDebit(txn.calculateTotalDebit())
                .totalCredit(txn.calculateTotalCredit())
                .balanced(txn.isBalanced())
                .createdAt(txn.getCreatedAt())
                .entries(entryResponses)
                .build();
    }
}
