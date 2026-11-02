package com.hookflow.backend.auth;

import com.hookflow.backend.security.CurrentUser;
import com.hookflow.backend.tenant.Tenant;
import com.hookflow.backend.tenant.TenantMembership;
import com.hookflow.backend.tenant.TenantMembershipRepository;
import com.hookflow.backend.tenant.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MeService {

    private final UserRepository users;
    private final TenantRepository tenants;
    private final TenantMembershipRepository memberships;

    public MeService(UserRepository users,
                     TenantRepository tenants,
                     TenantMembershipRepository memberships) {
        this.users = users;
        this.tenants = tenants;
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    public MeResponse me(CurrentUser current) {
        User user = users.findById(current.userId())
                .filter(found -> "ACTIVE".equals(found.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        List<TenantMembership> active = memberships.findByUserId(user.getId()).stream()
                .filter(m -> "ACTIVE".equals(m.getStatus()))
                .toList();

        List<UUID> tenantIds = active.stream().map(TenantMembership::getTenantId).toList();
        Map<UUID, Tenant> tenantById = tenants.findAllById(tenantIds).stream()
                .collect(Collectors.toMap(Tenant::getId, Function.identity()));

        List<MeResponse.MembershipInfo> infos = active.stream()
                .filter(m -> tenantById.containsKey(m.getTenantId()))
                .map(m -> new MeResponse.MembershipInfo(
                        m.getTenantId(),
                        tenantById.get(m.getTenantId()).getName(),
                        m.getRole()))
                .toList();

        return new MeResponse(
                user.getId(), user.getEmail(),
                current.tenantId(), current.role(), infos);
    }
}