package com.app.questr.dto.dashboard;

import java.util.List;

/**
 * Full dashboard payload returned by {@code GET /api/v1/dashboard}.
 *
 * <ul>
 *   <li>{@code xpToNextLevel} — XP still needed to reach {@code level + 1},
 *       derived from the inverse of the level formula:
 *       {@code minXpForLevel(n) = 100n² - 10n}</li>
 *   <li>{@code completionRate} — percentage of all user tasks that are completed
 *       (rounded to 1 decimal place, 0–100).</li>
 *   <li>{@code weeklyCompletions} — 7 entries, Mon–Sun of the current week
 *       (days with no completions have count = 0).</li>
 *   <li>{@code categoryBreakdown} — per-category completed-task counts &amp;
 *       percentages; empty when no tasks have been completed yet.</li>
 * </ul>
 */
public record DashboardResponse(
        int totalXp,
        int level,
        int xpToNextLevel,
        int currentStreak,
        int longestStreak,
        int tasksCompleted,
        double completionRate,
        List<WeeklyCompletionEntry> weeklyCompletions,
        List<CategoryBreakdownEntry> categoryBreakdown
) {}

