package com.app.questr.dto.auth;
import java.util.UUID;
public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, UUID userId, String username, String email) {}
