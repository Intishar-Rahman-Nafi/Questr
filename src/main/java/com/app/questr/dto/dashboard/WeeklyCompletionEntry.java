package com.app.questr.dto.dashboard;

import java.time.LocalDate;

/**
 * One slot in the 7-day weekly bar chart.
 * {@code dayOfWeek} is the JDK enum name (e.g. "MONDAY").
 */
public record WeeklyCompletionEntry(String dayOfWeek, LocalDate date, long count, long xpEarned) {}

