package com.openbounty.repository.projection;

import com.openbounty.enums.BountyCategory;

import java.math.BigDecimal;

/**
 * Spring Data JPA interface-based projection for aggregating category statistics.
 * Avoids loading full entity trees into heap memory for read-only analytics.
 */
public interface CategoryStatsProjection {
    BountyCategory getCategory();
    long getBountyCount();
    BigDecimal getTotalRewardAmount();
}
