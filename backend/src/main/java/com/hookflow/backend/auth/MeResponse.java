package com.hookflow.backend.auth;

import java.util.List;
import java.util.UUID;

public record MeResponse(
        UUID userId,
        String email,
        UUID tenantId,
        String role,
        List<MembershipInfo> memberships
) {
    public record MembershipInfo(UUID tenantId, String tenantName, String role) {}
}