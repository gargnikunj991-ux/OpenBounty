package com.openbounty.dto.response.proposal;

import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.ProposalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response payload confirming atomic acceptance of a winning proposal.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Proposal acceptance confirmation payload")
public class ProposalAcceptResponse {

    @Schema(description = "Accepted proposal ID", example = "201")
    private Long proposalId;

    @Schema(description = "Associated bounty ID", example = "101")
    private Long bountyId;

    @Schema(description = "Updated proposal status", example = "ACCEPTED")
    private ProposalStatus status;

    @Schema(description = "Assigned developer ID", example = "1")
    private Long assignedDeveloperId;

    @Schema(description = "Updated bounty status", example = "ASSIGNED")
    private BountyStatus bountyStatus;

    @Schema(description = "Confirmation message", example = "Proposal accepted successfully. Winning developer assigned.")
    private String message;
}
