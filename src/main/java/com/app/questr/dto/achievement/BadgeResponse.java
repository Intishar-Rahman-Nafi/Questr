package com.app.questr.dto.achievement;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Unified badge response DTO.
 *
 * <ul>
 *   <li>If {@code earned == true}, {@code earnedAt} is populated and
 *       {@code progressHint} is null.</li>
 *   <li>If {@code earned == false}, {@code earnedAt} is null and
 *       {@code progressHint} describes current progress towards the badge.</li>
 * </ul>
 */
public record BadgeResponse(
        UUID          id,
        String        name,
        String        displayName,
        String        description,
        String        iconUrl,
        String        badgeType,
        Integer       rewardXp,
        boolean       earned,
        LocalDateTime earnedAt,
        String        progressHint
) {}

