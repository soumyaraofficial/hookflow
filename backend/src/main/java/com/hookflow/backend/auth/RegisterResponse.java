package com.hookflow.backend.auth;

import java.util.UUID;

public record RegisterResponse(
        UUID userId,
        String email,
        UUID tenantId,
        String tenantName,
        String role
) {
}