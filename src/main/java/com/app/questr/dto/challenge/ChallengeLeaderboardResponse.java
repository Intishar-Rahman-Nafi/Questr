package com.app.questr.dto.challenge;

import java.util.List;
import java.util.UUID;

/**
 * Full leaderboard response for GET /api/v1/challenges/{id}/leaderboard.
 *
 * <p>Entries are ordered by {@code currentXp} descending (rank 1 = most XP).
 */
public record ChallengeLeaderboardResponse(
        UUID                            challengeId,
        String                          challengeName,
        int                             targetXp,
        List<ChallengeLeaderboardEntry> entries
) {}

