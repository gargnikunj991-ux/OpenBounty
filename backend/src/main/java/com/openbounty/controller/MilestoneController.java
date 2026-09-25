package com.openbounty.controller;

import com.openbounty.dto.request.milestone.MilestoneSubmitRequest;
import com.openbounty.dto.response.ErrorResponse;
import com.openbounty.dto.response.common.ApiResponse;
import com.openbounty.dto.response.milestone.MilestoneApproveResponse;
import com.openbounty.dto.response.milestone.MilestoneResponse;
import com.openbounty.security.UserPrincipal;
import com.openbounty.service.MilestoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller managing milestone deliverable submissions, sequential progress tracking,
 * client approvals, and deliverable revision loops.
 */
@RestController
@RequestMapping("/api/milestones")
@RequiredArgsConstructor
@Tag(name = "Milestones", description = "Milestone deliverable verification, progress tracking, and approval lifecycle")
public class MilestoneController {

    private final MilestoneService milestoneService;

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Submit milestone deliverable", description = "Submits proof of work (GitHub PR link, demo URL) for an assigned milestone. Enforces sequential execution.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Milestone submitted successfully",
                    content = @Content(schema = @Schema(implementation = MilestoneResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failure or out-of-order milestone submission",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Caller is not the assigned developer",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Milestone not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Conflict - Milestone is not in PENDING state",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MilestoneResponse> submitDeliverable(
            @Parameter(description = "Milestone unique identifier", example = "301")
            @PathVariable Long id,
            @Valid @RequestBody MilestoneSubmitRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        MilestoneResponse response = milestoneService.submitDeliverable(id, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Approve milestone deliverable", description = "Client verifies and approves a submitted deliverable. Automatically triggers bounty completion when 100% of milestones are approved.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Milestone approved successfully",
                    content = @Content(schema = @Schema(implementation = MilestoneApproveResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Caller is not the bounty owner",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Milestone not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Conflict - Milestone is not in SUBMITTED state or concurrent modification conflict",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MilestoneApproveResponse> approveMilestone(
            @Parameter(description = "Milestone unique identifier", example = "301")
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        MilestoneApproveResponse response = milestoneService.approveMilestone(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/request-revision")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Request deliverable revisions", description = "Client requests corrections on a submitted milestone, reverting its status back to PENDING.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Revision requested; milestone reset to PENDING",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Caller is not the bounty owner",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Milestone not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Conflict - Milestone is not in SUBMITTED state",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse> requestRevision(
            @Parameter(description = "Milestone unique identifier", example = "301")
            @PathVariable Long id,
            @RequestParam(required = false) String feedbackNotes,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ApiResponse response = milestoneService.requestRevision(id, feedbackNotes, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get milestone by ID", description = "Retrieves deliverable status and submission timestamps for a milestone. Only authorized participants can view.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Milestone retrieved successfully",
                    content = @Content(schema = @Schema(implementation = MilestoneResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Caller lacks permission to view milestone",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Milestone not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MilestoneResponse> getMilestoneById(
            @Parameter(description = "Milestone unique identifier", example = "301")
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        MilestoneResponse response = milestoneService.getMilestoneById(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/proposal/{proposalId}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List milestones for a proposal", description = "Retrieves all milestones belonging to a proposal ordered by chronological sequence.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Milestones retrieved successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = MilestoneResponse.class)))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Caller lacks permission to view milestones",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Proposal not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<MilestoneResponse>> getMilestonesByProposal(
            @Parameter(description = "Proposal ID", example = "201")
            @PathVariable Long proposalId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        List<MilestoneResponse> response = milestoneService.getMilestonesByProposal(proposalId, currentUser);
        return ResponseEntity.ok(response);
    }
}
