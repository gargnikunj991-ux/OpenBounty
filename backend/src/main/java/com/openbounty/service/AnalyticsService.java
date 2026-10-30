package com.openbounty.service;

import com.openbounty.dto.response.analytics.AnalyticsOverviewResponse;
import com.openbounty.dto.response.analytics.CategoryDistributionResponse;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.Role;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service providing aggregated platform analytics, high-level KPIs,
 * funds disbursed calculations, and category distribution metrics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final BountyRepository bountyRepository;
    private final UserRepository userRepository;

    /**
     * Compute and aggregate platform-wide high-level metrics and KPIs.
     *
     * @return High-level analytics overview containing counts and disbursed funds
     */
    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse getOverview() {
        log.info("Computing platform analytics overview metrics");

        long open = bountyRepository.countByStatus(BountyStatus.OPEN);
        long inReview = bountyRepository.countByStatus(BountyStatus.IN_REVIEW);
        long assigned = bountyRepository.countByStatus(BountyStatus.ASSIGNED);
        long inProgress = bountyRepository.countByStatus(BountyStatus.IN_PROGRESS);
        long completed = bountyRepository.countByStatus(BountyStatus.COMPLETED);
        long cancelled = bountyRepository.countByStatus(BountyStatus.CANCELLED);

        long totalBounties = open + inReview + assigned + inProgress + completed + cancelled;
        long activeBounties = open + inReview + assigned + inProgress;

        BigDecimal fundsDisbursed = bountyRepository.sumTotalCompletedRewardAmount();
        if (fundsDisbursed == null) {
            fundsDisbursed = BigDecimal.ZERO;
        }

        long totalDevelopers = userRepository.countByRole(Role.ROLE_DEVELOPER);
        long totalClients = userRepository.countByRole(Role.ROLE_CLIENT);

        return AnalyticsOverviewResponse.builder()
                .totalBounties(totalBounties)
                .activeBounties(activeBounties)
                .completedBounties(completed)
                .totalFundsDisbursed(fundsDisbursed)
                .totalDevelopers(totalDevelopers)
                .totalClients(totalClients)
                .build();
    }

    /**
     * Retrieve category breakdown statistics showing challenge counts and total reward funds.
     *
     * @return List of category distribution entries
     */
    @Transactional(readOnly = true)
    public List<CategoryDistributionResponse> getCategoryDistribution() {
        log.info("Computing category distribution statistics");

        return bountyRepository.getCategoryStatistics().stream()
                .map(CategoryDistributionResponse::from)
                .toList();
    }
}
