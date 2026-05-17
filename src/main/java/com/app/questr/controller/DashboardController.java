package com.app.questr.controller;
import com.app.questr.dto.dashboard.DashboardResponse;
import com.app.questr.dto.dashboard.WeeklyHistoryEntry;
import com.app.questr.security.UserPrincipal;
import com.app.questr.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
/**
 * Module 6 — Dashboard Analytics REST API.
 *
 * GET /api/v1/dashboard         - full dashboard snapshot (Redis-cached 5 min)
 * GET /api/v1/dashboard/history - multi-week trend data (?weeks=4, max 12)
 *
 * All endpoints require a valid JWT Bearer token.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;
    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(dashboardService.getUserDashboard(principal.getId()));
    }
    @GetMapping("/history")
    public ResponseEntity<List<WeeklyHistoryEntry>> getHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "4") int weeks) {
        return ResponseEntity.ok(dashboardService.getHistory(principal.getId(), weeks));
    }
}
