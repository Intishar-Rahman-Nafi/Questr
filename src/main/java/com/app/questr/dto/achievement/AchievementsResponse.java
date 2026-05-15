package com.app.questr.dto.achievement;

import java.util.List;

/**
 * Container response for the {@code GET /api/v1/achievements} endpoint.
 * Shows both earned trophies and locked badges with progress hints.
 */
public record AchievementsResponse(
        int              earnedCount,
        int              totalCount,
        List<BadgeResponse> earned,
        List<BadgeResponse> locked
) {}

