package com.app.questr.dto.dashboard;

import java.time.LocalDate;

/**
 * One week's completed-task count for the multi-week history chart.
 * {@code weekStart} is always a Monday; {@code weekEnd} is the following Sunday.
 */
public record WeeklyHistoryEntry(LocalDate weekStart, LocalDate weekEnd, int tasksCompleted) {}

