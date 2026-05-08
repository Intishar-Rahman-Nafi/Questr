package com.app.questr.model.projection;

import java.time.LocalDate;

/**
 * Spring Data projection for the task-completion-per-day query.
 * Used to build the weekly bar chart on the Dashboard.
 */
public interface DailyCompletionProjection {
    LocalDate getDay();
    Long getCount();
}

