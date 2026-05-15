package com.app.questr.dto.achievement;

/**
 * A single entry in the {@code GET /api/v1/achievements/leaderboard} response.
 */
public record LeaderboardEntry(
        int    rank,
        String username,
        int    level,
        int    totalXp,
        int    currentStreak
) {}

