package com.app.questr.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.sql.DataSource;

/**
 * Manual Flyway configuration for Spring Boot 4.
 *
 * <p>Spring Boot 4 removed the bundled {@code FlywayAutoConfiguration} that
 * was present in Spring Boot 3.x. This class replicates the essential
 * behaviour: run {@code flyway.migrate()} early in the context lifecycle so
 * that the schema is fully migrated before Hibernate validates or uses it.
 *
 * <p>Ordering guarantee: the {@code entityManagerFactory} bean created by
 * Hibernate's auto‑configuration picks up the {@code DataSource} bean. We
 * wrap the migration call inside the {@code flyway} bean's init so it runs as
 * part of normal Spring IoC construction—before any JPA operation tries to
 * touch the database.
 *
 * <p>Disable per profile with {@code spring.flyway.enabled=false} (e.g. tests
 * use {@code ddl-auto: create-drop} instead).
 */
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfig {

    @Value("${spring.flyway.locations:classpath:db/migration}")
    private String[] locations;

    @Value("${spring.flyway.baseline-on-migrate:true}")
    private boolean baselineOnMigrate;

    @Value("${spring.flyway.validate-on-migrate:true}")
    private boolean validateOnMigrate;

    @Value("${spring.flyway.out-of-order:false}")
    private boolean outOfOrder;

    /**
     * Creates and immediately migrates the database schema.
     *
     * <p>The {@code initMethod = "migrate"} approach is intentional: the
     * migration runs synchronously during bean initialisation, which Spring
     * guarantees happens before any bean that depends on {@link DataSource}
     * (including Hibernate's {@code LocalContainerEntityManagerFactoryBean}).
     *
     * <p><b>Note:</b> if you need strict ordering on a custom EMF bean, add
     * {@code @DependsOn("flyway")} to that bean.
     */
    @Bean
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations(locations)
            .baselineOnMigrate(baselineOnMigrate)
            .validateOnMigrate(validateOnMigrate)
            .outOfOrder(outOfOrder)
            .load();

        flyway.migrate();
        return flyway;
    }
}

