package com.app.questr.controller;
import com.app.questr.dto.report.AIReportResponse;
import com.app.questr.security.UserPrincipal;
import com.app.questr.service.OpenAIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
/**
 * Module 8 — AI Weekly Report REST API.
 *
 * GET  /api/v1/reports/weekly            — return cached report (24h TTL)
 * POST /api/v1/reports/weekly/regenerate — force-regenerate, rate-limited 1/day (429 if exceeded)
 *
 * All endpoints require a valid JWT Bearer token.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportController {
    private final OpenAIService openAIService;
    /**
     * Retrieve the AI-generated weekly productivity report.
     * Served from Redis cache (24 h TTL) or freshly generated.
     */
    @GetMapping("/weekly")
    public ResponseEntity<AIReportResponse> getWeeklyReport(
            @AuthenticationPrincipal UserPrincipal principal) {
        log.debug("GET /weekly requested by user {}", principal.getId());
        return ResponseEntity.ok(openAIService.getWeeklyReport(principal.getId()));
    }
    /**
     * Force-regenerate the weekly report, bypassing cache.
     * Rate-limited: 1 regeneration per user per day (returns 429 when exceeded).
     */
    @PostMapping("/weekly/regenerate")
    public ResponseEntity<AIReportResponse> regenerateWeeklyReport(
            @AuthenticationPrincipal UserPrincipal principal) {
        log.debug("POST /weekly/regenerate requested by user {}", principal.getId());
        return ResponseEntity.ok(openAIService.regenerateWeeklyReport(principal.getId()));
    }
}
