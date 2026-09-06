package com.openbounty.dto.response.analytics;

import com.openbounty.enums.BountyCategory;
import com.openbounty.repository.projection.CategoryStatsProjection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Breakdown of challenge counts and total reward funds allocated per category.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Category distribution statistics")
public class CategoryDistributionResponse {

    @Schema(description = "Technical domain category", example = "BACKEND_API")
    private BountyCategory category;

    @Schema(description = "Number of challenges posted under this category", example = "95")
    private long bountyCount;

    @Schema(description = "Total reward funds allocated in USD", example = "142500.00")
    private BigDecimal totalRewardAmount;

    public static CategoryDistributionResponse from(CategoryStatsProjection projection) {
        if (projection == null) {
            return null;
        }
        return CategoryDistributionResponse.builder()
                .category(projection.getCategory())
                .bountyCount(projection.getBountyCount())
                .totalRewardAmount(projection.getTotalRewardAmount() != null ? projection.getTotalRewardAmount() : BigDecimal.ZERO)
                .build();
    }
}
