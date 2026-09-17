package com.openbounty.controller;

import com.openbounty.dto.request.bounty.BountyCreateRequest;
import com.openbounty.dto.response.ErrorResponse;
import com.openbounty.dto.response.bounty.BountyCancelResponse;
import com.openbounty.dto.response.bounty.BountyResponse;
import com.openbounty.dto.response.bounty.BountySummaryResponse;
import com.openbounty.dto.response.common.PagedResponse;
import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.security.UserPrincipal;
import com.openbounty.service.BountyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller exposing endpoints for creating, searching, viewing, and cancelling bounties.
 */
@RestController
@RequestMapping("/api/bounties")
@RequiredArgsConstructor
@Tag(name = "Bounties", description = "Bounty / challenge lifecycle management, creation, marketplace search, and cancellation")
public class BountyController {

    private final BountyService bountyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a new bounty", description = "Creates a new technical challenge in OPEN status posted by an authenticated client.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Bounty successfully created",
                    content = @Content(schema = @Schema(implementation = BountyResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failure on request payload",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT Bearer token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Authenticated user lacks ROLE_CLIENT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BountyResponse> createBounty(
            @Valid @RequestBody BountyCreateRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        BountyResponse response = bountyService.createBounty(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List and search bounties", description = "Public endpoint providing paginated cards of bounties filtered by status, category, or keyword.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bounties retrieved successfully")
    })
    public ResponseEntity<PagedResponse<BountySummaryResponse>> getBounties(
            @Parameter(description = "Filter by bounty status (e.g. OPEN, ASSIGNED, COMPLETED)")
            @RequestParam(required = false) BountyStatus status,
            @Parameter(description = "Filter by domain category (e.g. BACKEND_API, AI_ML)")
            @RequestParam(required = false) BountyCategory category,
            @Parameter(description = "Free-text keyword search across title and description")
            @RequestParam(required = false) String search,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PagedResponse<BountySummaryResponse> response = bountyService.getBounties(status, category, search, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get bounty details by ID", description = "Public endpoint retrieving full challenge details, requirements, client profile, and assigned developer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bounty details retrieved successfully",
                    content = @Content(schema = @Schema(implementation = BountyResponse.class))),
            @ApiResponse(responseCode = "404", description = "Bounty not found with given ID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BountyResponse> getBountyById(
            @Parameter(description = "Bounty ID", example = "101")
            @PathVariable Long id) {
        BountyResponse response = bountyService.getBountyById(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cancel a bounty", description = "Cancels an open or in-review bounty. Only the creating client owner or an administrator can cancel.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bounty cancelled successfully",
                    content = @Content(schema = @Schema(implementation = BountyCancelResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Caller is not the bounty creator",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Bounty not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invalid state transition (bounty is already assigned or completed)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BountyCancelResponse> cancelBounty(
            @Parameter(description = "Bounty ID to cancel", example = "101")
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        BountyCancelResponse response = bountyService.cancelBounty(id, currentUser);
        return ResponseEntity.ok(response);
    }
}
