package com.openbounty.service;

import com.openbounty.dto.response.analytics.AnalyticsOverviewResponse;
import com.openbounty.dto.response.analytics.CategoryDistributionResponse;
import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.Role;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.UserRepository;
import com.openbounty.repository.projection.CategoryStatsProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private BountyRepository bountyRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    @DisplayName("getOverview: aggregates status counts, calculates total funds disbursed and user counts")
    void testGetOverview_Success() {
        when(bountyRepository.countByStatus(BountyStatus.OPEN)).thenReturn(50L);
        when(bountyRepository.countByStatus(BountyStatus.IN_REVIEW)).thenReturn(10L);
        when(bountyRepository.countByStatus(BountyStatus.ASSIGNED)).thenReturn(20L);
        when(bountyRepository.countByStatus(BountyStatus.IN_PROGRESS)).thenReturn(15L);
        when(bountyRepository.countByStatus(BountyStatus.COMPLETED)).thenReturn(100L);
        when(bountyRepository.countByStatus(BountyStatus.CANCELLED)).thenReturn(5L);

        when(bountyRepository.sumTotalCompletedRewardAmount()).thenReturn(new BigDecimal("250000.00"));
        when(userRepository.countByRole(Role.ROLE_DEVELOPER)).thenReturn(850L);
        when(userRepository.countByRole(Role.ROLE_CLIENT)).thenReturn(210L);

        AnalyticsOverviewResponse overview = analyticsService.getOverview();

        assertThat(overview).isNotNull();
        assertThat(overview.getTotalBounties()).isEqualTo(200L); // 50+10+20+15+100+5
        assertThat(overview.getActiveBounties()).isEqualTo(95L); // 50+10+20+15
        assertThat(overview.getCompletedBounties()).isEqualTo(100L);
        assertThat(overview.getTotalFundsDisbursed()).isEqualByComparingTo("250000.00");
        assertThat(overview.getTotalDevelopers()).isEqualTo(850L);
        assertThat(overview.getTotalClients()).isEqualTo(210L);
    }

    @Test
    @DisplayName("getOverview: handles null funds disbursed by defaulting to BigDecimal.ZERO")
    void testGetOverview_NullFundsDisbursedDefaultsToZero() {
        when(bountyRepository.countByStatus(BountyStatus.OPEN)).thenReturn(0L);
        when(bountyRepository.countByStatus(BountyStatus.IN_REVIEW)).thenReturn(0L);
        when(bountyRepository.countByStatus(BountyStatus.ASSIGNED)).thenReturn(0L);
        when(bountyRepository.countByStatus(BountyStatus.IN_PROGRESS)).thenReturn(0L);
        when(bountyRepository.countByStatus(BountyStatus.COMPLETED)).thenReturn(0L);
        when(bountyRepository.countByStatus(BountyStatus.CANCELLED)).thenReturn(0L);

        when(bountyRepository.sumTotalCompletedRewardAmount()).thenReturn(null);
        when(userRepository.countByRole(Role.ROLE_DEVELOPER)).thenReturn(0L);
        when(userRepository.countByRole(Role.ROLE_CLIENT)).thenReturn(0L);

        AnalyticsOverviewResponse overview = analyticsService.getOverview();

        assertThat(overview).isNotNull();
        assertThat(overview.getTotalFundsDisbursed()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getCategoryDistribution: maps repository projection to CategoryDistributionResponse DTOs")
    void testGetCategoryDistribution_Success() {
        CategoryStatsProjection p1 = mock(CategoryStatsProjection.class);
        when(p1.getCategory()).thenReturn(BountyCategory.BACKEND_API);
        when(p1.getBountyCount()).thenReturn(35L);
        when(p1.getTotalRewardAmount()).thenReturn(new BigDecimal("70000.00"));

        CategoryStatsProjection p2 = mock(CategoryStatsProjection.class);
        when(p2.getCategory()).thenReturn(BountyCategory.AI_ML);
        when(p2.getBountyCount()).thenReturn(20L);
        when(p2.getTotalRewardAmount()).thenReturn(new BigDecimal("50000.00"));

        when(bountyRepository.getCategoryStatistics()).thenReturn(List.of(p1, p2));

        List<CategoryDistributionResponse> distribution = analyticsService.getCategoryDistribution();

        assertThat(distribution).hasSize(2);
        assertThat(distribution.get(0).getCategory()).isEqualTo(BountyCategory.BACKEND_API);
        assertThat(distribution.get(0).getBountyCount()).isEqualTo(35L);
        assertThat(distribution.get(0).getTotalRewardAmount()).isEqualByComparingTo("70000.00");

        assertThat(distribution.get(1).getCategory()).isEqualTo(BountyCategory.AI_ML);
        assertThat(distribution.get(1).getBountyCount()).isEqualTo(20L);
        assertThat(distribution.get(1).getTotalRewardAmount()).isEqualByComparingTo("50000.00");
    }
}
