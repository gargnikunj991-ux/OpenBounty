package com.openbounty.model;

import com.openbounty.enums.ProposalStatus;
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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "proposals",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_bounty_developer", columnNames = {"bounty_id", "developer_id"})
    },
    indexes = {
        @Index(name = "idx_proposals_bounty_id", columnList = "bounty_id"),
        @Index(name = "idx_proposals_developer_id", columnList = "developer_id"),
        @Index(name = "idx_proposals_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"bounty", "developer", "milestones"})
public class Proposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bounty_id", nullable = false)
    private Bounty bounty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "developer_id", nullable = false)
    private User developer;

    @Column(name = "approach_description", nullable = false, columnDefinition = "TEXT")
    private String approachDescription;

    @Column(name = "proposed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal proposedAmount;

    @Column(name = "estimated_days", nullable = false)
    private Integer estimatedDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ProposalStatus status = ProposalStatus.PENDING;

    @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Milestone> milestones = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void addMilestone(Milestone milestone) {
        milestones.add(milestone);
        milestone.setProposal(this);
    }

    public void removeMilestone(Milestone milestone) {
        milestones.remove(milestone);
        milestone.setProposal(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Proposal proposal = (Proposal) o;
        return id != null && id.equals(proposal.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
