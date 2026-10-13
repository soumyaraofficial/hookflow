
package com.hookflow.backend;

import com.hookflow.backend.auth.User;
import com.hookflow.backend.auth.UserRepository;
import com.hookflow.backend.tenant.Tenant;
import com.hookflow.backend.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import com.hookflow.backend.tenant.TenantMembership;
import com.hookflow.backend.tenant.TenantMembershipRepository;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PersistenceIntegrationTest {
    @Autowired
    TenantMembershipRepository memberships;
    @Autowired
    TenantRepository tenants;

    @Autowired
    UserRepository users;

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
}
