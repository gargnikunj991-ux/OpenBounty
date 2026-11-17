package com.openbounty.controller;

import com.openbounty.dto.request.milestone.DisputeCreateRequest;
import com.openbounty.dto.request.milestone.DisputeResolveRequest;
import com.openbounty.dto.response.common.PagedResponse;
import com.openbounty.dto.response.milestone.MilestoneDisputeResponse;
import com.openbounty.security.UserPrincipal;
import com.openbounty.service.MilestoneDisputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing milestone arbitration dispute APIs and administrative settlement workflows.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Disputes & Arbitration", description = "Milestone dispute filing and administrative escrow settlement APIs")
public class DisputeController {

    private final MilestoneDisputeService disputeService;

    @PostMapping("/api/milestones/{id}/dispute")
    @Operation(summary = "File a milestone dispute", description = "Escalates deliverable disagreement to admin arbitration and freezes escrow into suspense.")
    public ResponseEntity<MilestoneDisputeResponse> fileDispute(
            @PathVariable Long id,
            @Valid @RequestBody DisputeCreateRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        log.info("Dispute submission received for milestone ID {} from user ID {}", id, currentUser.getId());
        MilestoneDisputeResponse response = disputeService.fileDispute(id, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/disputes/{id}")
    @Operation(summary = "Get dispute details", description = "Fetches dispute details with participant IDOR verification.")
    public ResponseEntity<MilestoneDisputeResponse> getDisputeById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        MilestoneDisputeResponse response = disputeService.getDisputeById(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/disputes")
    @Operation(summary = "List disputes", description = "Returns paginated list of disputes (Admin sees all; participants see their own).")
    public ResponseEntity<PagedResponse<MilestoneDisputeResponse>> getDisputes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<MilestoneDisputeResponse> disputePage = disputeService.getDisputes(currentUser, pageable);

        return ResponseEntity.ok(PagedResponse.from(disputePage));
    }

    @PostMapping("/api/admin/disputes/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Resolve a milestone dispute", description = "Administrator decree settling dispute and releasing suspense escrow (Admin only).")
    public ResponseEntity<MilestoneDisputeResponse> resolveDispute(
            @PathVariable Long id,
            @Valid @RequestBody DisputeResolveRequest request,
            @AuthenticationPrincipal UserPrincipal adminUser) {

        log.info("Admin ID {} resolving dispute ID {} with decision: {}", adminUser.getId(), id, request.getDecision());
        MilestoneDisputeResponse response = disputeService.resolveDispute(id, request, adminUser);
        return ResponseEntity.ok(response);
    }
}
