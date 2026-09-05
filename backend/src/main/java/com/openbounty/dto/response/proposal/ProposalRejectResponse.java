package com.openbounty.dto.response.proposal;

import com.openbounty.enums.ProposalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response payload confirming rejection of a proposal.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Proposal rejection response payload")
public class ProposalRejectResponse {

    @Schema(description = "Rejected proposal ID", example = "201")
    private Long proposalId;

    @Schema(description = "Updated proposal status", example = "REJECTED")
    private ProposalStatus status;
}
