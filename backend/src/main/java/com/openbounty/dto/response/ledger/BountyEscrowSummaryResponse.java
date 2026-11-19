package com.openbounty.dto.response.ledger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BountyEscrowSummaryResponse {

    private Long bountyId;
    private String bountyTitle;
    private BigDecimal totalRewardAmount;
    private BigDecimal currentLockedEscrow;
    private BigDecimal totalDisbursed;
    private BigDecimal totalRefunded;
    private List<LedgerTransactionResponse> transactions;
}
