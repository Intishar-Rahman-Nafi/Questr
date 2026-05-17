package com.app.questr.dto.dashboard;

/**
 * One slice of the category donut chart.
 * {@code percentage} is rounded to one decimal place (0–100).
 */
public record CategoryBreakdownEntry(String category, long count, double percentage) {}

