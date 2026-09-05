package com.openbounty.dto;

import com.openbounty.dto.response.auth.UserProfileResponse;
import com.openbounty.dto.response.auth.UserSummaryResponse;
import com.openbounty.dto.response.bounty.BountyResponse;
import com.openbounty.dto.response.bounty.BountySummaryResponse;
import com.openbounty.dto.response.common.PagedResponse;
import com.openbounty.dto.response.milestone.MilestoneResponse;
import com.openbounty.dto.response.proposal.ProposalResponse;
import com.openbounty.dto.response.review.ReviewResponse;
import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.MilestoneStatus;
import com.openbounty.enums.ProposalStatus;
import com.openbounty.enums.Role;
import com.openbounty.model.Bounty;
import com.openbounty.model.Milestone;
import com.openbounty.model.Proposal;
import com.openbounty.model.Review;
import com.openbounty.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseDtoMappingTest {

    @Test
    @DisplayName("UserSummaryResponse and UserProfileResponse correctly map entity fields")
    void testUserMapping() {
        User user = User.builder()
                .id(1L)
                .name("Alex Johnson")
                .email("alex@example.com")
                .password("hashed_secret")
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(50)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        UserSummaryResponse summary = UserSummaryResponse.from(user);
        assertThat(summary).isNotNull();
        assertThat(summary.getId()).isEqualTo(1L);
        assertThat(summary.getName()).isEqualTo("Alex Johnson");
        assertThat(summary.getRole()).isEqualTo(Role.ROLE_DEVELOPER);
        assertThat(summary.getReputationScore()).isEqualTo(50);

        UserProfileResponse profile = UserProfileResponse.from(user);
        assertThat(profile).isNotNull();
        assertThat(profile.getEmail()).isEqualTo("alex@example.com");
        assertThat(profile.getCreatedAt()).isEqualTo(user.getCreatedAt());
    }

    @Test
    @DisplayName("BountyResponse and BountySummaryResponse correctly map nested client and dev profiles")
    void testBountyMapping() {
        User client = User.builder()
                .id(10L)
                .name("Acme Corp")
                .email("client@acme.com")
                .role(Role.ROLE_CLIENT)
                .reputationScore(100)
                .build();

        User dev = User.builder()
                .id(20L)
                .name("Dev Alex")
                .email("alex@dev.io")
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(85)
                .build();

        Bounty bounty = Bounty.builder()
                .id(101L)
                .title("Build Auth Engine")
                .description("Detailed instructions")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1500.00"))
                .status(BountyStatus.ASSIGNED)
                .deadline(LocalDate.now().plusWeeks(2))
                .client(client)
                .assignedDeveloper(dev)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        BountyResponse response = BountyResponse.from(bounty);
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getClient().getName()).isEqualTo("Acme Corp");
        assertThat(response.getAssignedDeveloper().getName()).isEqualTo("Dev Alex");
        assertThat(response.getCategory()).isEqualTo(BountyCategory.BACKEND_API);

        BountySummaryResponse summary = BountySummaryResponse.from(bounty);
        assertThat(summary).isNotNull();
        assertThat(summary.getTitle()).isEqualTo("Build Auth Engine");
        assertThat(summary.getClient().getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("ProposalResponse maps nested milestones without infinite recursion")
    void testProposalMapping() {
        User dev = User.builder().id(5L).name("Solver").role(Role.ROLE_DEVELOPER).build();
        Bounty bounty = Bounty.builder().id(99L).title("Test Bounty").build();

        Proposal proposal = Proposal.builder()
                .id(201L)
                .bounty(bounty)
                .developer(dev)
                .approachDescription("Architecture approach")
                .proposedAmount(new BigDecimal("900.00"))
                .estimatedDays(5)
                .status(ProposalStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        Milestone m1 = Milestone.builder()
                .id(301L)
                .proposal(proposal)
                .title("Deliverable 1")
                .status(MilestoneStatus.PENDING)
                .build();

        proposal.setMilestones(List.of(m1));

        ProposalResponse response = ProposalResponse.from(proposal);
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(201L);
        assertThat(response.getBountyId()).isEqualTo(99L);
        assertThat(response.getDeveloper().getName()).isEqualTo("Solver");
        assertThat(response.getMilestones()).hasSize(1);
        assertThat(response.getMilestones().get(0).getTitle()).isEqualTo("Deliverable 1");
    }

    @Test
    @DisplayName("ReviewResponse maps reviewer, reviewee and ratings")
    void testReviewMapping() {
        User reviewer = User.builder().id(1L).name("Reviewer").build();
        User reviewee = User.builder().id(2L).name("Reviewee").build();
        Bounty bounty = Bounty.builder().id(50L).build();

        Review review = Review.builder()
                .id(401L)
                .bounty(bounty)
                .reviewer(reviewer)
                .reviewee(reviewee)
                .rating(5)
                .feedback("Superb work!")
                .createdAt(LocalDateTime.now())
                .build();

        ReviewResponse response = ReviewResponse.from(review);
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(401L);
        assertThat(response.getBountyId()).isEqualTo(50L);
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getReviewer().getId()).isEqualTo(1L);
        assertThat(response.getReviewee().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("PagedResponse envelope maps Spring Data Page correctly")
    void testPagedResponseMapping() {
        User u1 = User.builder().id(1L).name("User 1").build();
        User u2 = User.builder().id(2L).name("User 2").build();

        Page<User> page = new PageImpl<>(List.of(u1, u2), PageRequest.of(0, 10), 2);
        PagedResponse<UserSummaryResponse> pagedResponse = PagedResponse.from(page, UserSummaryResponse::from);

        assertThat(pagedResponse.getContent()).hasSize(2);
        assertThat(pagedResponse.getPageNumber()).isEqualTo(0);
        assertThat(pagedResponse.getPageSize()).isEqualTo(10);
        assertThat(pagedResponse.getTotalElements()).isEqualTo(2);
        assertThat(pagedResponse.getTotalPages()).isEqualTo(1);
        assertThat(pagedResponse.isFirst()).isTrue();
        assertThat(pagedResponse.isLast()).isTrue();
        assertThat(pagedResponse.isEmpty()).isFalse();
    }
}
