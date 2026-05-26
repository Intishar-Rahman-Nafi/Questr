package com.app.questr.dto.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Module 8 — AI Weekly Report response DTO.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code summary}     — 2–3 sentence narrative of the user's week</li>
 *   <li>{@code tips}        — 3 personalised productivity tips</li>
 *   <li>{@code improvements}— 1–2 sentences on areas needing work</li>
 *   <li>{@code quote}       — motivational quote with attribution</li>
 *   <li>{@code weekStart}   — Monday of the report week</li>
 *   <li>{@code weekEnd}     — Sunday of the report week</li>
 *   <li>{@code generatedAt} — timestamp when the report was created</li>
 *   <li>{@code fallback}    — {@code true} if the OpenAI call failed and a
 *                              static fallback was returned instead</li>
 * </ul>
 */
public record AIReportResponse(
        String        summary,
        List<String>  tips,
        String        improvements,
        String        quote,
        LocalDate     weekStart,
        LocalDate     weekEnd,
        LocalDateTime generatedAt,
        boolean       fallback
) {}

