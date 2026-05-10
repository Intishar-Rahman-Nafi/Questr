package com.app.questr.dto.task;

import com.app.questr.model.enums.TaskCategory;
import com.app.questr.model.enums.TaskPriority;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable response DTO returned by every TaskController endpoint.
 * Never exposes internal entity references (no User object).
 */
public record TaskResponse(
        UUID           id,
        String         title,
        String         description,
        TaskCategory   category,
        TaskPriority   priority,
        LocalDateTime  deadline,
        Boolean        completed,
        Integer        xpValue,
        LocalDateTime  createdAt,
        LocalDateTime  completedAt
) {}

