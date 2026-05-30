package com.app.questr.model.projection;

import java.time.LocalDate;

/**
 * Spring Data projection for the XP-earned-per-day query.
 * Used to populate the weekly trend chart on the Dashboard.
 */
public interface DailyXpProjection {
    LocalDate getDay();
    Long getXp();
}

