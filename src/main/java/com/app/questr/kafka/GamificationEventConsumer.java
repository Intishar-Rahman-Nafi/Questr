package com.app.questr.kafka;

import com.app.questr.config.KafkaConfig;
import com.app.questr.event.XPUpdateEvent;
import com.app.questr.model.entity.ChallengeParticipant;
import com.app.questr.repository.ChallengeParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Kafka consumer for gamification events.
 *
 * <p>Listens on {@code xp-events} and:
 * <ol>
 *   <li>Logs the event for observability.</li>
 *   <li>Increments {@code currentXp} for any active challenge the user is
 *       participating in, keeping leaderboards up to date.</li>
 * </ol>
 *
 * <p>{@code @Transactional} is required so that the lazy-loaded
 * {@code ChallengeParticipant.challenge} proxy can be navigated when checking
 * whether a challenge is currently active. The {@link Acknowledgment} is always
 * committed (in {@code finally}) to prevent message re-delivery on consumer
 * restart after a non-fatal processing error.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GamificationEventConsumer {

    private final ChallengeParticipantRepository challengeParticipantRepository;

    @KafkaListener(
            topics  = KafkaConfig.XP_EVENTS_TOPIC,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    @Transactional
    public void handleXpEvent(XPUpdateEvent event, Acknowledgment ack) {
        try {
            log.info("XP event received: user={} +{}xp total={} level={} leveledUp={}",
                    event.getUserId(), event.getXpGained(),
                    event.getNewTotalXp(), event.getNewLevel(), event.isLeveledUp());

            updateActiveChallengeParticipants(event);

        } catch (Exception e) {
            log.error("Error processing XP event for user {}: {}",
                    event.getUserId(), e.getMessage(), e);
        } finally {
            ack.acknowledge(); // always commit — avoid poison-pill re-delivery
        }
    }

    /** Increments challenge-scoped XP for every active challenge the user is in. */
    private void updateActiveChallengeParticipants(XPUpdateEvent event) {
        LocalDateTime now = LocalDateTime.now();
        List<ChallengeParticipant> participations =
                challengeParticipantRepository.findByUserId(event.getUserId());

        for (ChallengeParticipant cp : participations) {
            var challenge = cp.getChallenge(); // lazy-loaded within @Transactional
            if (challenge.getStartDate().isBefore(now) && challenge.getEndDate().isAfter(now)) {
                int updated = challengeParticipantRepository.addXp(
                        challenge.getId(), event.getUserId(), event.getXpGained());
                if (updated > 0) {
                    log.debug("Challenge {} XP +{} for user {}",
                            challenge.getId(), event.getXpGained(), event.getUserId());
                }
            }
        }
    }
}

