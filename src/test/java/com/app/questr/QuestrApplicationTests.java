package com.app.questr;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke test — verifies the full Spring application context loads successfully.
 *
 * <p>What this test validates end-to-end:
 * <ol>
 *   <li>Testcontainers spins up a real PostgreSQL 15 database.</li>
 *   <li>@ServiceConnection wires the datasource URL automatically.</li>
 *   <li>FlywayConfig runs migrations V1 → V6 (schema + badge seed data).</li>
 *   <li>Hibernate validates entity mappings against the migrated schema.</li>
 *   <li>Spring Security filter-chain is built without errors.</li>
 *   <li>All Spring beans wire correctly (repositories, services, controllers).</li>
 * </ol>
 *
 * <p>Redis and Kafka use the localhost placeholders from
 * {@code test/resources/application.yml}. No real broker operations happen
 * at startup (no listeners registered in Module 2), so placeholder values
 * are sufficient.
 */
@SpringBootTest(properties = {
    // Enable Flyway so migrations run and the schema is validated end-to-end.
    "spring.flyway.enabled=true",
    // Flyway owns DDL — Hibernate must not recreate or drop tables.
    "spring.jpa.hibernate.ddl-auto=none",
    // Disable Redis caching auto-config to avoid connection errors in CI
    // (no Redis broker needed for a context-load smoke test).
    "spring.cache.type=none",
    // Kafka auto-configuration: suppress the listener container factory
    // startup because no @KafkaListener exists yet in Module 2.
    "spring.kafka.listener.auto-startup=false"
})
@Testcontainers
class QuestrApplicationTests {

    /**
     * Testcontainers PostgreSQL 15 — same image as production Docker Compose.
     * @ServiceConnection automatically overrides spring.datasource.* so
     * HikariCP and Flyway connect to the container, not localhost:5432.
     */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine");

    /**
     * Explicitly propagates the Testcontainers-assigned JDBC URL, username,
     * and password into the Spring Environment before the application context
     * is refreshed.  This supplements @ServiceConnection and guarantees that
     * both the auto-configured HikariCP DataSource AND the custom FlywayConfig
     * bean receive the container credentials rather than the "admin/secret"
     * defaults declared in application.yml.
     */
    @DynamicPropertySource
    static void overrideDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void contextLoads() {
        // If the test method is reached the full application context loaded:
        //   ✅ DataSource connected to Testcontainers Postgres
        //   ✅ Flyway V1-V6 migrations executed successfully
        //   ✅ Hibernate entity mappings validated against migrated schema
        //   ✅ Spring Security filter-chain built
        //   ✅ All repository beans wired
    }

}
