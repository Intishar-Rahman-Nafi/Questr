package com.app.questr.service;

import com.app.questr.dto.dashboard.CategoryBreakdownEntry;
import com.app.questr.dto.dashboard.DashboardResponse;
import com.app.questr.dto.dashboard.WeeklyCompletionEntry;
import com.app.questr.dto.dashboard.WeeklyHistoryEntry;
import com.app.questr.exception.ResourceNotFoundException;
import com.app.questr.model.entity.UserStats;
import com.app.questr.model.projection.CategoryBreakdownProjection;
import com.app.questr.model.projection.DailyCompletionProjection;
import com.app.questr.model.projection.DailyXpProjection;
import com.app.questr.repository.TaskRepository;
import com.app.questr.repository.UserStatsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Module 6 — Dashboard Analytics Service.
 *
 * <p>Pipeline for {@link #getUserDashboard(UUID)}:
 * <ol>
 *   <li>Redis cache look-up (5-min TTL, best-effort — errors are swallowed).</li>
 *   <li>Fetch {@link UserStats} from DB.</li>
 *   <li>Fetch completed-task counts for the current Mon–Sun week.</li>
 *   <li>Fetch category breakdown for the donut chart.</li>
 *   <li>Compute completion-rate and XP-to-next-level.</li>
 *   <li>Store result in Redis and return.</li>
 * </ol>
 *
 * <p>Level formula inversion: {@code minXpForLevel(n) = 100n² − 10n}.
 * Derived from the forward formula {@code level = floor((√(100·xp+25)+5)/100)}.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private static final String   DASHBOARD_CACHE_PREFIX = "dashboard:";
    private static final Duration DASHBOARD_CACHE_TTL    = Duration.ofMinutes(5);

    private final UserStatsRepository           userStatsRepository;
    private final TaskRepository                taskRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper                  objectMapper;

    // ── Main dashboard ────────────────────────────────────────────────────────

    public DashboardResponse getUserDashboard(UUID userId) {
        String cacheKey = DASHBOARD_CACHE_PREFIX + userId;

        // 1. Try Redis cache
        DashboardResponse cached = readFromCache(cacheKey, DashboardResponse.class);
        if (cached != null) {
            log.debug("Dashboard cache hit for user {}", userId);
            return cached;
        }

        // 2. UserStats (must exist — created at registration)
        UserStats stats = userStatsRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("UserStats", userId));

        // 3. weekly completions (Monday → Sunday of current ISO week)
        LocalDate    today     = LocalDate.now();
        LocalDate    weekStart = today.with(DayOfWeek.MONDAY);
        LocalDateTime queryFrom = weekStart.atStartOfDay();

        List<DailyCompletionProjection> rawDaily =
                taskRepository.getWeeklyCompletions(userId, queryFrom);
        List<DailyXpProjection> rawXp =
                taskRepository.getWeeklyXpEarned(userId, queryFrom);
        List<WeeklyCompletionEntry> weeklyCompletions =
                buildWeeklyEntries(rawDaily, rawXp, weekStart);

        // 4. Category breakdown
        List<CategoryBreakdownProjection> rawCategories =
                taskRepository.getCategoryBreakdown(userId);
        long totalCompleted = taskRepository.countByUserIdAndCompletedTrue(userId);
        List<CategoryBreakdownEntry> categoryBreakdown =
                buildCategoryBreakdown(rawCategories, totalCompleted);

        // 5. Completion rate
        long totalTasks    = taskRepository.countByUserId(userId);
        double completionRate = totalTasks > 0
                ? Math.round((double) totalCompleted / totalTasks * 1000.0) / 10.0
                : 0.0;

        // 6. XP to next level
        int xpToNextLevel = computeXpToNextLevel(stats.getLevel(), stats.getTotalXp());

        DashboardResponse response = new DashboardResponse(
                stats.getTotalXp(),
                stats.getLevel(),
                xpToNextLevel,
                stats.getCurrentStreak(),
                stats.getLongestStreak(),
                stats.getTasksCompleted(),
                completionRate,
                weeklyCompletions,
                categoryBreakdown
        );

        // 7. Write to cache (best-effort)
        writeToCache(cacheKey, response, DASHBOARD_CACHE_TTL);

        return response;
    }

    // ── History ───────────────────────────────────────────────────────────────

    /**
     * Returns per-week completed-task counts for the last {@code weeks} weeks
     * (including the current partial week). Capped at 12 to prevent abuse.
     */
    public List<WeeklyHistoryEntry> getHistory(UUID userId, int weeks) {
        int capped = Math.min(Math.max(weeks, 1), 12);

        LocalDate    today   = LocalDate.now();
        LocalDate    monday  = today.with(DayOfWeek.MONDAY);
        // Start from Monday of the oldest week we care about
        LocalDateTime since  = monday.minusWeeks(capped - 1L).atStartOfDay();

        List<DailyCompletionProjection> raw =
                taskRepository.getWeeklyHistoryCompletions(userId, since);

        // Build map: weekStart (Monday) → count
        Map<LocalDate, Long> byWeek = raw.stream()
                .collect(Collectors.toMap(
                        DailyCompletionProjection::getDay,
                        DailyCompletionProjection::getCount));

        // Fill all N weeks, oldest first
        List<WeeklyHistoryEntry> result = new ArrayList<>(capped);
        for (int i = capped - 1; i >= 0; i--) {
            LocalDate ws = monday.minusWeeks(i);
            LocalDate we = ws.plusDays(6);
            result.add(new WeeklyHistoryEntry(ws, we, byWeek.getOrDefault(ws, 0L).intValue()));
        }
        return result;
    }

    // ── Formula helpers ───────────────────────────────────────────────────────

    /**
     * Minimum XP required to reach level {@code n}: {@code 100n² − 10n}.
     * Derived by inverting {@code level = floor((sqrt(100·xp+25)+5)/100)}.
     *
     * <p>Examples: level 2 → 380 XP, level 3 → 870 XP.
     */
    public static int xpFloorForLevel(int level) {
        return 100 * level * level - 10 * level;
    }

    /**
     * XP still needed to advance from the current level to the next.
     * Never negative (returns 0 if the user has already "over-earned" for their level).
     */
    public static int computeXpToNextLevel(int currentLevel, int totalXp) {
        int xpForNext = xpFloorForLevel(currentLevel + 1);
        return Math.max(0, xpForNext - totalXp);
    }

    // ── Weekly grid builder ───────────────────────────────────────────────────

    /**
     * Returns exactly 7 entries (Mon–Sun) for the current week, backfilling
     * days with zero completions and zero XP.
     */
    private List<WeeklyCompletionEntry> buildWeeklyEntries(
            List<DailyCompletionProjection> raw,
            List<DailyXpProjection> rawXp,
            LocalDate weekStart) {

        Map<LocalDate, Long> byDay = raw.stream()
                .collect(Collectors.toMap(
                        DailyCompletionProjection::getDay,
                        DailyCompletionProjection::getCount));

        Map<LocalDate, Long> xpByDay = rawXp.stream()
                .collect(Collectors.toMap(
                        DailyXpProjection::getDay,
                        DailyXpProjection::getXp));

        List<WeeklyCompletionEntry> result = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            result.add(new WeeklyCompletionEntry(
                    date.getDayOfWeek().name(),
                    date,
                    byDay.getOrDefault(date, 0L),
                    xpByDay.getOrDefault(date, 0L)));
        }
        return result;
    }

    // ── Category breakdown builder ────────────────────────────────────────────

    private List<CategoryBreakdownEntry> buildCategoryBreakdown(
            List<CategoryBreakdownProjection> raw, long totalCompleted) {

        if (raw.isEmpty() || totalCompleted == 0) return List.of();

        return raw.stream()
                .map(p -> {
                    double pct = Math.round(p.getCount() * 1000.0 / totalCompleted) / 10.0;
                    return new CategoryBreakdownEntry(p.getCategory(), p.getCount(), pct);
                })
                .toList();
    }

    // ── Redis helpers (best-effort) ───────────────────────────────────────────

    private <T> T readFromCache(String key, Class<T> type) {
        try {
            Object raw = redisTemplate.opsForValue().get(key);
            if (raw == null) return null;
            // raw may be a LinkedHashMap (no type headers); convertValue handles it
            return objectMapper.convertValue(raw, type);
        } catch (Exception e) {
            log.warn("Redis cache read failed for key '{}': {}", key, e.getMessage());
            return null;
        }
    }

    private void writeToCache(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Redis cache write failed for key '{}': {}", key, e.getMessage());
        }
    }
}

