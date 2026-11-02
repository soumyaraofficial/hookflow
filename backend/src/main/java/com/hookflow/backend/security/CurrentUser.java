package com.hookflow.backend.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public record CurrentUser(UUID userId, UUID tenantId, String role) {

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString("tenant_id")),
                jwt.getClaimAsString("role"));
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}