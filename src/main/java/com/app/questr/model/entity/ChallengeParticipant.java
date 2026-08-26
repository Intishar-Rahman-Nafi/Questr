package com.app.questr.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Represents one user's participation in a {@link Challenge}.
 * Uses a composite PK ({@link ChallengeParticipantId}) — both challenge_id
 * and user_id are required.
 *
 * <p>{@code currentXp} starts at 0 and is incremented by the Kafka event
 * consumer whenever the participant earns XP while the challenge is active.
 * It drives the leaderboard ordering.
 */
@Entity
@Table(
    name = "challenge_participants",
    indexes = {
        @Index(name = "idx_challenge_participants_user", columnList = "user_id")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"challenge", "user"})
public class ChallengeParticipant {

    @EmbeddedId
    private ChallengeParticipantId id;

    /** @MapsId maps the embedded PK field to the FK join column. */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("challengeId")
    @JoinColumn(name = "challenge_id", nullable = false)
    // Breaks the Challenge.participants <-> ChallengeParticipant.challenge cycle.
    @JsonIgnore
    private Challenge challenge;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    // Breaks any User <-> ChallengeParticipant cycle for Jackson.
    @JsonIgnore
    private User user;

    @Column(name = "current_xp", nullable = false)
    @Builder.Default
    private Integer currentXp = 0;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;
}

