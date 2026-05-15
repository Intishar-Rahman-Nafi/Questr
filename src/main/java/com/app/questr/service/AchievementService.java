package com.app.questr.service;

import com.app.questr.dto.achievement.AchievementsResponse;
import com.app.questr.dto.achievement.BadgeResponse;
import com.app.questr.dto.achievement.LeaderboardEntry;
import com.app.questr.model.entity.Badge;
import com.app.questr.model.entity.UserBadge;
import com.app.questr.model.entity.UserStats;
import com.app.questr.repository.BadgeRepository;
import com.app.questr.repository.UserBadgeRepository;
import com.app.questr.repository.UserStatsRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-side service for the Achievements & Leaderboard APIs.
 *
 * <p>All methods are read-only transactions so lazy-loaded relationships
 * (e.g. {@code UserStats.user}) can be accessed without detached-entity errors.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class AchievementService {

    private final BadgeRepository     badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final UserStatsRepository userStatsRepository;
    private final ObjectMapper        objectMapper;

    // ── Achievements ──────────────────────────────────────────────────────────

    /**
     * Returns all 18 badges split into earned (with timestamp) and
     * locked (with a progress hint derived from current stats).
     */
    public AchievementsResponse getAchievements(UUID userId) {
        // Fetch current stats for progress calculation
        UserStats stats = userStatsRepository.findByUserId(userId).orElse(null);

        // All badges the user has already earned
        List<UserBadge> userBadges = userBadgeRepository.findByUserId(userId);
        Set<String> earnedNames = userBadges.stream()
                .map(ub -> ub.getBadge().getName())
                .collect(Collectors.toSet());

        List<Badge> allBadges = badgeRepository.findAll();

        List<BadgeResponse> earned  = new ArrayList<>();
        List<BadgeResponse> locked  = new ArrayList<>();

        for (Badge badge : allBadges) {
            if (earnedNames.contains(badge.getName())) {
                UserBadge ub = userBadges.stream()
                        .filter(u -> u.getBadge().getName().equals(badge.getName()))
                        .findFirst().orElseThrow();
                earned.add(toBadgeResponse(badge, true, ub.getEarnedAt(), null));
            } else {
                String hint = buildProgressHint(badge, stats);
                locked.add(toBadgeResponse(badge, false, null, hint));
            }
        }

        return new AchievementsResponse(earned.size(), allBadges.size(), earned, locked);
    }

    // ── Leaderboard ───────────────────────────────────────────────────────────

    /**
     * Top-10 users by total XP.
     * {@code @Transactional(readOnly = true)} keeps the session open so
     * {@code stats.getUser().getUsername()} (lazy) resolves without error.
     */
    public List<LeaderboardEntry> getLeaderboard() {
        List<UserStats> top10 = userStatsRepository.findTop10ByOrderByTotalXpDesc();
        List<LeaderboardEntry> result = new ArrayList<>();
        for (int i = 0; i < top10.size(); i++) {
            UserStats s = top10.get(i);
            result.add(new LeaderboardEntry(
                    i + 1,
                    s.getUser().getUsername(),
                    s.getLevel(),
                    s.getTotalXp(),
                    s.getCurrentStreak()));
        }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BadgeResponse toBadgeResponse(Badge badge, boolean earned,
                                          java.time.LocalDateTime earnedAt,
                                          String progressHint) {
        return new BadgeResponse(
                badge.getId(),
                badge.getName(),
                badge.getDisplayName(),
                badge.getDescription(),
                badge.getIconUrl(),
                badge.getBadgeType().name(),
                badge.getRewardXp(),
                earned,
                earnedAt,
                progressHint);
    }

    /**
     * Produces a human-readable progress string for a locked badge.
     * Returns {@code null} if stats are unavailable or criteria cannot be parsed.
     */
    private String buildProgressHint(Badge badge, UserStats stats) {
        if (stats == null || badge.getCriteria() == null) return null;
        try {
            Map<String, Object> c = objectMapper.readValue(
                    badge.getCriteria(), new TypeReference<>() {});
            String type = (String) c.get("type");
            if (type == null) return null;

            return switch (type) {
                case "STREAK"     -> stats.getCurrentStreak() + " / " + c.get("days") + " days";
                case "XP"         -> stats.getTotalXp()       + " / " + c.get("threshold") + " XP";
                case "TASK_COUNT" -> stats.getTasksCompleted() + " / " + c.get("count") + " tasks";
                case "LEVEL"      -> "Level " + stats.getLevel() + " / " + c.get("level");
                case "TIME"       -> "Complete a task before " + c.get("before");
                default           -> null;
            };
        } catch (Exception e) {
            log.debug("Cannot build progress hint for badge '{}': {}", badge.getName(), e.getMessage());
            return null;
        }
    }
}

