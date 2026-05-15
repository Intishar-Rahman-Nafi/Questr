package com.app.questr.controller;

import com.app.questr.dto.achievement.AchievementsResponse;
import com.app.questr.dto.achievement.LeaderboardEntry;
import com.app.questr.security.UserPrincipal;
import com.app.questr.service.AchievementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Module 5 — Achievements & Leaderboard REST API.
 *
 * <pre>
 * GET /api/v1/achievements             - earned badges + locked with progress hints
 * GET /api/v1/achievements/leaderboard - top 10 users by XP
 * </pre>
 *
 * All endpoints require a valid JWT Bearer token.
 */
@RestController
@RequestMapping("/api/v1/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;

    /**
     * Returns all badges split into earned and locked.
     * Locked badges include a progress hint towards the next milestone.
     */
    @GetMapping
    public ResponseEntity<AchievementsResponse> getAchievements(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(achievementService.getAchievements(principal.getId()));
    }

    /**
     * Returns the top 10 users ranked by total XP descending.
     * Visible to any authenticated user — useful for social motivation.
     */
    @GetMapping("/leaderboard")
    public ResponseEntity<List<LeaderboardEntry>> getLeaderboard() {
        return ResponseEntity.ok(achievementService.getLeaderboard());
    }
}

