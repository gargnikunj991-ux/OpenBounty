package com.openbounty.dto.response.ledger;

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
public class PlatformBalanceSheetResponse {

    private BigDecimal platformCash;
    private BigDecimal clientEscrowLocked;
    private BigDecimal developerPayable;
    private BigDecimal platformFeeRevenue;
    private BigDecimal disputeSuspense;
    private boolean balanced;
    private LocalDateTime generatedAt;
}
