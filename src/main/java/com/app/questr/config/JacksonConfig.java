package com.app.questr.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Exposes a shared {@link ObjectMapper} bean with Java 8 date/time support.
 *
 * <p>{@code findAndRegisterModules()} auto-discovers all Jackson modules on
 * the classpath (including {@code jackson-datatype-jsr310} for LocalDateTime
 * serialisation) without requiring a direct import of the module class.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}

