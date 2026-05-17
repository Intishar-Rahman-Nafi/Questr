package com.app.questr.service;

import com.app.questr.config.KafkaConfig;
import com.app.questr.event.XPUpdateEvent;
import com.app.questr.exception.ResourceNotFoundException;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core gamification engine — Module 5.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Award XP and recalculate level / streak in {@link UserStats}.</li>
 *   <li>Check every badge condition and idempotently award new badges.</li>
 *   <li>Publish {@link XPUpdateEvent} to Kafka (best-effort).</li>
 *   <li>Cache updated stats in Redis with a 1-hour TTL (best-effort).</li>
 * </ol>
 *
 * <p>Level formula: {@code level = max(1, floor((sqrt(100 * totalXp + 25) + 5) / 100))}
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class GamificationService {

    private static final String   STATS_CACHE_PREFIX     = "user-stats:";
    private static final String   DASHBOARD_CACHE_PREFIX = "dashboard:";
    private static final Duration STATS_CACHE_TTL        = Duration.ofHours(1);

    private final UserStatsRepository                  userStatsRepository;
    private final BadgeRepository                      badgeRepository;
    private final UserBadgeRepository                  userBadgeRepository;
    private final KafkaTemplate<String, XPUpdateEvent> kafkaTemplate;
    private final RedisTemplate<String, Object>        redisTemplate;
    private final ObjectMapper                         objectMapper;

    // ── Public API ────────────────────────────────────────────────────────────

    public void awardXP(UUID userId, int xpAmount) {
        UserStats stats = userStatsRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("UserStats", userId));

        int oldLevel = stats.getLevel();

        // 1. Add XP
        int newTotal = stats.getTotalXp() + xpAmount;
        stats.setTotalXp(newTotal);

        // 2. Recalculate level
        int newLevel = calculateLevel(newTotal);
        if (newLevel > oldLevel) {
            log.info("User {} leveled up: {} → {}", userId, oldLevel, newLevel);
            stats.setLevel(newLevel);
        }

        // 3. Increment task counter
        stats.setTasksCompleted(stats.getTasksCompleted() + 1);

        // 4. Update daily streak
        LocalDate today = LocalDate.now();
        LocalDate last  = stats.getLastActivityDate();
        if (last == null) {
            stats.setCurrentStreak(1);
        } else if (last.equals(today)) {
            log.debug("Already active today — streak unchanged for user {}", userId);
        } else if (last.equals(today.minusDays(1))) {
            stats.setCurrentStreak(stats.getCurrentStreak() + 1);
        } else {
            stats.setCurrentStreak(1); // gap ≥ 2 days → reset
        }
        if (stats.getCurrentStreak() > stats.getLongestStreak()) {
            stats.setLongestStreak(stats.getCurrentStreak());
        }
        stats.setLastActivityDate(today);

        userStatsRepository.save(stats);

        // 5. Idempotent badge evaluation
        checkAndAwardBadges(stats);

        // 6. Publish XP event to Kafka (best-effort — failure never rolls back DB)
        publishXpEvent(userId, xpAmount, stats, oldLevel);

        // 7. Cache latest stats in Redis (best-effort)
        cacheStats(userId, stats);

        // 8. Invalidate the dashboard cache so the next GET /dashboard reflects
        //    the updated XP / level / streak immediately (best-effort).
        evictDashboardCache(userId);

        log.info("XP +{} user={} total={} level={} streak={}",
                xpAmount, userId, stats.getTotalXp(), stats.getLevel(), stats.getCurrentStreak());
    }

    /** Public so callers and tests can verify the formula independently. */
    public static int calculateLevel(int totalXp) {
        if (totalXp <= 0) return 1;
        return Math.max(1, (int) Math.floor((Math.sqrt(100.0 * totalXp + 25) + 5) / 100));
    }

    // ── Badge evaluation ──────────────────────────────────────────────────────

    /**
     * Iterates every badge definition and awards any whose conditions are
     * now met. Already-earned badges are skipped — safe to call many times.
     */
    void checkAndAwardBadges(UserStats stats) {
        UUID userId = stats.getUser().getId();
        List<Badge> allBadges = badgeRepository.findAll();

        for (Badge badge : allBadges) {
            if (userBadgeRepository.hasBadge(userId, badge.getName())) {
                continue; // idempotency guard
            }
            if (evaluateCriteria(badge, stats)) {
                userBadgeRepository.save(
                        UserBadge.builder()
                                .user(stats.getUser())
                                .badge(badge)
                                .build());
                log.info("Badge '{}' awarded to user {}", badge.getName(), userId);

                // Add bonus XP directly (no recursive badge-check to avoid loops)
                if (badge.getRewardXp() > 0) {
                    int updatedXp = stats.getTotalXp() + badge.getRewardXp();
                    stats.setTotalXp(updatedXp);
                    int updatedLevel = calculateLevel(updatedXp);
                    if (updatedLevel > stats.getLevel()) {
                        stats.setLevel(updatedLevel);
                        log.info("Badge reward level-up: user {} → level {}", userId, updatedLevel);
                    }
                    userStatsRepository.save(stats);
                }
            }
        }
    }

    /**
     * Parses the badge's JSON criteria and tests it against current stats.
     * Returns {@code false} on any error to avoid blocking XP flow.
     */
    private boolean evaluateCriteria(Badge badge, UserStats stats) {
        try {
            String raw = badge.getCriteria();
            if (raw == null || raw.isBlank()) return false;

            Map<String, Object> c = objectMapper.readValue(raw, new TypeReference<>() {});
            String type = (String) c.get("type");
            if (type == null) return false;

            return switch (type) {
                case "STREAK"     -> stats.getCurrentStreak() >= toInt(c.get("days"));
                case "XP"         -> stats.getTotalXp()       >= toInt(c.get("threshold"));
                case "TASK_COUNT" -> stats.getTasksCompleted() >= toInt(c.get("count"));
                case "LEVEL"      -> stats.getLevel()          >= toInt(c.get("level"));
                case "TIME" -> {
                    String before = (String) c.get("before");
                    yield before != null && LocalTime.now().isBefore(LocalTime.parse(before));
                }
                default -> {
                    log.debug("Unknown badge criteria type '{}' for '{}'", type, badge.getName());
                    yield false;
                }
            };
        } catch (Exception e) {
            log.warn("Cannot evaluate badge '{}': {}", badge.getName(), e.getMessage());
            return false;
        }
    }

    private static int toInt(Object value) {
        if (value instanceof Integer i) return i;
        if (value instanceof Number  n) return n.intValue();
        return 0;
    }

    // ── Kafka ─────────────────────────────────────────────────────────────────

    private void publishXpEvent(UUID userId, int xpAmount, UserStats stats, int oldLevel) {
        try {
            XPUpdateEvent event = XPUpdateEvent.builder()
                    .userId(userId)
                    .xpGained(xpAmount)
                    .newTotalXp(stats.getTotalXp())
                    .newLevel(stats.getLevel())
                    .leveledUp(stats.getLevel() > oldLevel)
                    .timestamp(Instant.now())
                    .build();
            kafkaTemplate.send(KafkaConfig.XP_EVENTS_TOPIC, userId.toString(), event);
        } catch (Exception e) {
            log.warn("Kafka XP event publish failed for user {}: {}", userId, e.getMessage());
        }
    }

    // ── Redis ─────────────────────────────────────────────────────────────────

    private void cacheStats(UUID userId, UserStats stats) {
        try {
            redisTemplate.opsForValue().set(STATS_CACHE_PREFIX + userId, stats, STATS_CACHE_TTL);
        } catch (Exception e) {
            log.warn("Redis cache update failed for user {}: {}", userId, e.getMessage());
        }
    }

    public void evictStatsCache(UUID userId) {
        try {
            redisTemplate.delete(STATS_CACHE_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Redis eviction failed for user {}: {}", userId, e.getMessage());
        }
    }

    private void evictDashboardCache(UUID userId) {
        try {
            redisTemplate.delete(DASHBOARD_CACHE_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Redis dashboard cache eviction failed for user {}: {}", userId, e.getMessage());
        }
    }
}



