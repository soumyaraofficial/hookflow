package com.hookflow.backend.auth;

import com.hookflow.backend.tenant.Tenant;
import com.hookflow.backend.tenant.TenantMembership;
import com.hookflow.backend.tenant.TenantMembershipRepository;
import com.hookflow.backend.tenant.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository users;
    private final TenantRepository tenants;
    private final TenantMembershipRepository memberships;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository users,
            TenantRepository tenants,
            TenantMembershipRepository memberships,
            PasswordEncoder passwordEncoder
    ) {
        this.users = users;
        this.tenants = tenants;
        this.memberships = memberships;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already registered"
            );
        }

        User user = users.saveAndFlush(
                new User(email, passwordEncoder.encode(request.password()))
        );

        Tenant tenant = tenants.saveAndFlush(
                new Tenant(request.tenantName().trim())
        );

        TenantMembership membership = memberships.saveAndFlush(
                new TenantMembership(
                        tenant.getId(),
                        user.getId(),
                        "ADMIN"
                )
        );

        return new RegisterResponse(
                user.getId(),
                user.getEmail(),
                tenant.getId(),
                tenant.getName(),
                membership.getRole()
        );
    }
}