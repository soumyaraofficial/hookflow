package com.hookflow.backend;
import org.springframework.web.server.ResponseStatusException;
import com.hookflow.backend.auth.AuthService;
import com.hookflow.backend.auth.RegisterRequest;
import com.hookflow.backend.auth.RegisterResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.hookflow.backend.auth.RefreshToken;
import com.hookflow.backend.auth.RefreshTokenRepository;
import com.hookflow.backend.auth.User;
import com.hookflow.backend.auth.UserRepository;
import com.hookflow.backend.tenant.Tenant;
import com.hookflow.backend.tenant.TenantMembership;
import com.hookflow.backend.tenant.TenantMembershipRepository;
import com.hookflow.backend.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PersistenceIntegrationTest {
    @Autowired
    AuthService authService;
    @Autowired
    TenantMembershipRepository memberships;

    @Autowired
    TenantRepository tenants;

    @Autowired
    UserRepository users;

    @Autowired
    RefreshTokenRepository refreshTokens;

    @Test
    void savesAndLoadsTenant() {
        Tenant saved = tenants.saveAndFlush(new Tenant("Acme"));

        Tenant loaded = tenants.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getName()).isEqualTo("Acme");
        assertThat(loaded.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void savesAndLoadsTenantMembership() {
        Tenant tenant = tenants.saveAndFlush(
                new Tenant("Membership Test " + UUID.randomUUID())
        );

        User user = users.saveAndFlush(
                new User(
                        "membership-" + UUID.randomUUID() + "@example.com",
                        "test-password-hash"
                )
        );

        TenantMembership saved = memberships.saveAndFlush(
                new TenantMembership(
                        tenant.getId(),
                        user.getId(),
                        "ADMIN"
                )
        );

        TenantMembership loaded = memberships
                .findByTenantIdAndUserId(tenant.getId(), user.getId())
                .orElseThrow();

        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.getRole()).isEqualTo("ADMIN");
        assertThat(loaded.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsDuplicateTenantMembership() {
        Tenant tenant = tenants.saveAndFlush(
                new Tenant("Duplicate Test " + UUID.randomUUID())
        );

        User user = users.saveAndFlush(
                new User(
                        "duplicate-" + UUID.randomUUID() + "@example.com",
                        "test-password-hash"
                )
        );

        memberships.saveAndFlush(
                new TenantMembership(
                        tenant.getId(),
                        user.getId(),
                        "ADMIN"
                )
        );

        TenantMembership duplicate = new TenantMembership(
                tenant.getId(),
                user.getId(),
                "MEMBER"
        );

        assertThatThrownBy(() -> memberships.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savesAndLoadsUser() {
        String email = "alice-" + UUID.randomUUID()
                + "@example.com";

        User saved = users.saveAndFlush(
                new User(email, "test-password-hash")
        );

        User loaded = users.findById(saved.getId())
                .orElseThrow();

        assertThat(loaded.getEmail()).isEqualTo(email);
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() {
        String email = "bob-" + UUID.randomUUID()
                + "@example.com";

        users.saveAndFlush(
                new User(email, "test-password-hash")
        );

        User duplicate = new User(
                email.toUpperCase(),
                "another-test-password-hash"
        );

        assertThatThrownBy(() -> users.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test
    void registersUserTenantAndAdminMembership() {
        String email = "register-" + UUID.randomUUID() + "@example.com";

        RegisterResponse response = authService.register(
                new RegisterRequest(email, "test-password-123", "Acme Test")
        );

        User user = users.findById(response.userId()).orElseThrow();
        Tenant tenant = tenants.findById(response.tenantId()).orElseThrow();
        TenantMembership membership = memberships
                .findByTenantIdAndUserId(tenant.getId(), user.getId())
                .orElseThrow();

        assertThat(response.email()).isEqualTo(email);
        assertThat(tenant.getName()).isEqualTo("Acme Test");
        assertThat(membership.getRole()).isEqualTo("ADMIN");
        assertThat(user.getPasswordHash()).isNotEqualTo("test-password-123");
    }

    @Test
    void rejectsRegistrationWithExistingEmail() {
        String email = "duplicate-register-" + UUID.randomUUID() + "@example.com";

        authService.register(
                new RegisterRequest(email, "test-password-123", "First Tenant")
        );

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest(email, "another-password-123", "Second Tenant")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }
    @Test
    void savesLoadsAndRevokesRefreshToken() {
        User user = users.saveAndFlush(
                new User(
                        "refresh-" + UUID.randomUUID() + "@example.com",
                        "test-password-hash"
                )
        );

        String tokenHash = UUID.randomUUID().toString();

        Tenant tenant = tenants.saveAndFlush(new Tenant("Refresh Test " + UUID.randomUUID()));

        RefreshToken saved = refreshTokens.saveAndFlush(
                new RefreshToken(
                        user.getId(),
                        tenant.getId(),
                        tokenHash,
                        Instant.now().plusSeconds(3600)
                )
        );

        RefreshToken loaded = refreshTokens
                .findByTokenHash(tokenHash)
                .orElseThrow();

        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.getTokenHash()).isEqualTo(tokenHash);
        assertThat(loaded.getRevokedAt()).isNull();

        loaded.revoke();
        refreshTokens.saveAndFlush(loaded);

        RefreshToken revoked = refreshTokens
                .findByTokenHash(tokenHash)
                .orElseThrow();

        assertThat(revoked.getRevokedAt()).isNotNull();
    }
}