package com.app.questr.dto.challenge;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response body for challenge endpoints.
 *
 * <ul>
 *   <li>{@code active}  — true only when {@code startDate ≤ now ≤ endDate}</li>
 *   <li>{@code creator} — true when the requesting user created this challenge</li>
 *   <li>{@code participantCount} — current number of joined participants</li>
 * </ul>
 */
public record ChallengeResponse(
        UUID            id,
        String          name,
        String          description,
        String          inviteCode,
        LocalDateTime   startDate,
        LocalDateTime   endDate,
        int             targetXp,
        UUID            createdById,
        String          createdByUsername,
        LocalDateTime   createdAt,
        int             participantCount,
        boolean         active,
        boolean         creator,
        /** true when the requesting user is currently a participant (including creator) */
        boolean         joined,
        /** the requesting user's accumulated XP in this challenge (0 if not a participant) */
        int             myCurrentXp
) {}

