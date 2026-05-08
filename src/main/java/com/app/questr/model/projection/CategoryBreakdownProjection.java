package com.app.questr.model.projection;

/**
 * Spring Data projection for the category-breakdown query.
 * Used to build the category donut chart on the Dashboard.
 */
public interface CategoryBreakdownProjection {
    String getCategory();
    Long getCount();
}

