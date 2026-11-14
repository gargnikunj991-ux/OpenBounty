package com.openbounty.dto.response.ledger;

import com.openbounty.enums.EntryType;
import com.openbounty.enums.LedgerAccount;
import com.openbounty.model.LedgerEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntryResponse {

    private Long id;
    private LedgerAccount account;
    private EntryType entryType;
    private BigDecimal amount;
    private LocalDateTime createdAt;

    public static LedgerEntryResponse from(LedgerEntry entry) {
        if (entry == null) return null;
        return LedgerEntryResponse.builder()
                .id(entry.getId())
                .account(entry.getAccount())
                .entryType(entry.getEntryType())
                .amount(entry.getAmount())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
