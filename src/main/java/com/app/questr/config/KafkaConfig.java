package com.app.questr.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic definitions for the gamification engine.
 *
 * <p>Topics are created automatically by the KafkaAdmin bean that
 * Spring Boot auto-configures when spring.kafka.bootstrap-servers is set.
 */
@Configuration
public class KafkaConfig {

    /** Topic for XP award events — consumed by GamificationEventConsumer. */
    public static final String XP_EVENTS_TOPIC    = "xp-events";

    /** Topic for badge unlock notifications — reserved for future use. */
    public static final String BADGE_EVENTS_TOPIC = "badge-events";

    @Bean
    public NewTopic xpEventsTopic() {
        return TopicBuilder.name(XP_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic badgeEventsTopic() {
        return TopicBuilder.name(BADGE_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}

