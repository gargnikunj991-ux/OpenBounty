package com.openbounty.dto.response.bounty;

import com.openbounty.enums.BountyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response payload confirming the cancellation of a bounty.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Bounty cancellation response payload")
public class BountyCancelResponse {

    @Schema(description = "Cancelled bounty ID", example = "101")
    private Long id;

    @Schema(description = "Updated status of the bounty", example = "CANCELLED")
    private BountyStatus status;

    @Schema(description = "Informative status message", example = "Bounty has been successfully cancelled.")
    private String message;
}
