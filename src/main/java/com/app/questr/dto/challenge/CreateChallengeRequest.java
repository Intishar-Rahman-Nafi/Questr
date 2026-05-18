package com.app.questr.dto.challenge;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Request body for POST /api/v1/challenges.
 *
 * <p>The {@code targetXp} is optional — defaults to 100 when omitted.
 * Both {@code startDate} and {@code endDate} must be provided; the service
 * enforces that {@code endDate} is strictly after {@code startDate}.
 */
public record CreateChallengeRequest(

        @NotBlank(message = "Challenge name must not be blank")
        @Size(max = 255, message = "Challenge name must not exceed 255 characters")
        String name,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        @NotNull(message = "startDate is required")
        LocalDateTime startDate,

        @NotNull(message = "endDate is required")
        @Future(message = "endDate must be in the future")
        LocalDateTime endDate,

        @Positive(message = "targetXp must be a positive number")
        Integer targetXp
) {}

