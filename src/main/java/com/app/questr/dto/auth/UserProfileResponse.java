package com.app.questr.dto.auth;
import java.time.LocalDateTime;
import java.util.UUID;
public record UserProfileResponse(UUID id, String username, String email, LocalDateTime createdAt) {}
