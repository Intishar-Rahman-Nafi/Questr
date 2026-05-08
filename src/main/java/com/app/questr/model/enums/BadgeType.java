package com.app.questr.model.enums;

/**
 * Categorises badges so the UI can group them in the Achievements gallery.
 */
public enum BadgeType {
    /** Earned by maintaining a daily task completion streak */
    STREAK,
    /** Earned by accumulating total XP milestones */
    XP_MILESTONE,
    /** Earned by completing a certain number of tasks */
    TASK_COUNT,
    /** Special time-based or behaviour-based badges */
    SPECIAL
}

