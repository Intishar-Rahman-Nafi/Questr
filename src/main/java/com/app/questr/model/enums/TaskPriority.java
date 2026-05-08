package com.app.questr.model.enums;

import lombok.Getter;

/**
 * Task priority levels with their associated XP reward.
 * High-priority tasks reward more XP to incentivise tackling hard things first.
 */
@Getter
public enum TaskPriority {
    LOW(5),
    MEDIUM(10),
    HIGH(20);

    private final int baseXp;

    TaskPriority(int baseXp) {
        this.baseXp = baseXp;
    }
}

