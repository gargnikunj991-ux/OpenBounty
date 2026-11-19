package com.openbounty.dto.request.milestone;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for filing an arbitration dispute on a milestone deliverable.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeCreateRequest {

    @NotBlank(message = "Dispute reason is required")
    @Size(min = 10, max = 2000, message = "Dispute reason must be between 10 and 2000 characters")
    private String reason;

    @Size(max = 500, message = "Evidence URL cannot exceed 500 characters")
    private String evidenceUrl;
}
