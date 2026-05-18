package com.app.questr.dto.challenge;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single rank row in a challenge leaderboard.
 *
 * <p>Ranks start at 1; ties share the same rank but still consume sequential
 * positions (standard competition ranking).
 */
public record ChallengeLeaderboardEntry(
        int           rank,
        UUID          userId,
        String        username,
        int           currentXp,
        LocalDateTime joinedAt
) {}

