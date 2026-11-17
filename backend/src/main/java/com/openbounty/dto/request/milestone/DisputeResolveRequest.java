package com.openbounty.dto.request.milestone;

import com.openbounty.enums.DisputeDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for administrator settlement of an arbitration dispute.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeResolveRequest {

    @NotNull(message = "Arbitration decision is required")
    private DisputeDecision decision;

    @NotBlank(message = "Resolution notes are required")
    @Size(min = 10, max = 2000, message = "Resolution notes must be between 10 and 2000 characters")
    private String resolutionNotes;
}
