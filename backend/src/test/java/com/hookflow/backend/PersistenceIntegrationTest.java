package com.hookflow.backend;

import com.hookflow.backend.tenant.Tenant;
import com.hookflow.backend.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PersistenceIntegrationTest {

    @Autowired
    TenantRepository tenants;

    @Test
    void savesAndLoadsTenant() {
        Tenant saved = tenants.saveAndFlush(new Tenant("Acme"));

        Tenant loaded = tenants.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getName()).isEqualTo("Acme");
        assertThat(loaded.getStatus()).isEqualTo("ACTIVE");
    }
}
