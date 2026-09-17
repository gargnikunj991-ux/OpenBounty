package com.openbounty.controller;

import com.openbounty.dto.request.proposal.ProposalCreateRequest;
import com.openbounty.dto.response.ErrorResponse;
import com.openbounty.dto.response.proposal.ProposalAcceptResponse;
import com.openbounty.dto.response.proposal.ProposalRejectResponse;
import com.openbounty.dto.response.proposal.ProposalResponse;
import com.openbounty.security.UserPrincipal;
import com.openbounty.service.ProposalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller exposing endpoints for submitting proposals, viewing proposals for a bounty,
 * atomically accepting winning bids, and rejecting proposals.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Proposals", description = "Proposal and bidding lifecycle management, developer bids, acceptance, and rejection")
public class ProposalController {

    private final ProposalService proposalService;

    @PostMapping("/bounties/{bountyId}/proposals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Submit a proposal", description = "Submits a technical solution proposal with structured milestones for an open bounty.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Proposal successfully submitted",
                    content = @Content(schema = @Schema(implementation = ProposalResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failure on request payload",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT Bearer token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Authenticated user lacks ROLE_DEVELOPER or attempted to bid on own bounty",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Bounty not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict - Duplicate proposal submission or invalid bounty state",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProposalResponse> submitProposal(
            @Parameter(description = "Target bounty ID", example = "101")
            @PathVariable Long bountyId,
            @Valid @RequestBody ProposalCreateRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ProposalResponse response = proposalService.submitProposal(bountyId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/bounties/{bountyId}/proposals")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List proposals for a bounty", description = "Retrieves all proposals submitted for a bounty. Only the bounty creator client or an administrator may view them.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proposals retrieved successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProposalResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Caller is not the bounty creator",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Bounty not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<ProposalResponse>> getProposalsByBounty(
            @Parameter(description = "Bounty ID", example = "101")
            @PathVariable Long bountyId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        List<ProposalResponse> response = proposalService.getProposalsByBounty(bountyId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/proposals/{id}/accept")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Accept proposal", description = "Atomically accepts a winning proposal, assigns the developer to the bounty, transitions bounty to ASSIGNED, and rejects all competing proposals.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proposal accepted successfully and competing bids rejected",
                    content = @Content(schema = @Schema(implementation = ProposalAcceptResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Caller is not the bounty creator",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Proposal not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict - Proposal is not PENDING or bounty is not in OPEN/IN_REVIEW state",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProposalAcceptResponse> acceptProposal(
            @Parameter(description = "Proposal ID to accept", example = "201")
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ProposalAcceptResponse response = proposalService.acceptProposal(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/proposals/{id}/reject")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Reject proposal", description = "Explicitly rejects a proposal submitted for a bounty.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proposal rejected successfully",
                    content = @Content(schema = @Schema(implementation = ProposalRejectResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Caller is not the bounty creator",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Proposal not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict - Proposal is not in PENDING state or bounty is completed/cancelled",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProposalRejectResponse> rejectProposal(
            @Parameter(description = "Proposal ID to reject", example = "201")
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ProposalRejectResponse response = proposalService.rejectProposal(id, currentUser);
        return ResponseEntity.ok(response);
    }
}
