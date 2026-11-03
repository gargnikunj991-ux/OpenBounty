package com.openbounty.controller;

import com.openbounty.dto.response.analytics.AnalyticsOverviewResponse;
import com.openbounty.dto.response.analytics.CategoryDistributionResponse;
import com.openbounty.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing public platform-wide analytics and category breakdown metrics.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Platform statistics, overview KPIs, and category distribution metrics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    @Operation(summary = "Get platform overview metrics",
            description = "Public endpoint returning high-level metrics including total bounties, active bounties, funds disbursed, and solver counts.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Overview metrics retrieved successfully",
                    content = @Content(schema = @Schema(implementation = AnalyticsOverviewResponse.class)))
    })
    public ResponseEntity<AnalyticsOverviewResponse> getOverview() {
        AnalyticsOverviewResponse response = analyticsService.getOverview();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/categories")
    @Operation(summary = "Get category distribution breakdown",
            description = "Public endpoint returning domain category statistics with challenge counts and allocated reward funds.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category statistics retrieved successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CategoryDistributionResponse.class))))
    })
    public ResponseEntity<List<CategoryDistributionResponse>> getCategoryDistribution() {
        List<CategoryDistributionResponse> response = analyticsService.getCategoryDistribution();
        return ResponseEntity.ok(response);
    }
}
