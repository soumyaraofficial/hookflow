package com.hookflow.backend.auth;

import java.time.Instant;
import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant accessTokenExpiresAt,
        UUID userId,
        UUID tenantId,
        String role
) {}