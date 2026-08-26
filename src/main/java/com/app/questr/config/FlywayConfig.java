package com.app.questr.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
 * Hibernate's auto-configuration picks up the {@code DataSource} bean. We
 * register an {@link EntityManagerFactoryDependsOnPostProcessor} (the same
 * mechanism Spring Boot's removed {@code FlywayAutoConfiguration} used) so that
 * the JPA session factory is built only AFTER the {@code flyway} bean has run
 * {@code migrate()} — otherwise Hibernate's {@code ddl-auto: validate} races
 * ahead of Flyway on a fresh database and fails with "missing table".
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
     * <p>{@code migrate()} runs synchronously inside this bean method, so the
     * migration completes before the bean is returned. Combined with the
     * {@link EntityManagerFactoryDependsOnPostProcessor} registered above, this
     * guarantees Hibernate never validates/uses an un-migrated schema.
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

    /**
     * Forces Hibernate's {@code entityManagerFactory} to be created after the
     * {@code flyway} bean. This bean MUST be {@code static} so it is registered
     * as a {@code BeanFactoryPostProcessor} early in the lifecycle, before any
     * ordinary bean (including the JPA session factory) is instantiated.
     */
    @Bean
    public static EntityManagerFactoryDependsOnPostProcessor flywayEntityManagerFactoryDependsOnPostProcessor() {
        return new EntityManagerFactoryDependsOnPostProcessor(Flyway.class);
    }
}

