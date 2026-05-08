package com.app.questr.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A social challenge that users can create and invite friends to join.
 * The creator of the challenge automatically becomes a participant.
 *
 * <p>Users join via a random {@code inviteCode} (6-char alphanumeric).
 * The leaderboard is built from {@link ChallengeParticipant#currentXp}.
 */
@Entity
@Table(
    name = "challenges",
    indexes = {
        @Index(name = "idx_challenges_invite_code",  columnList = "invite_code"),
        @Index(name = "idx_challenges_created_by",   columnList = "created_by"),
        @Index(name = "idx_challenges_end_date",     columnList = "end_date")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"createdBy", "participants"})
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Random 6-char alphanumeric code friends use to join. */
    @Column(name = "invite_code", unique = true, nullable = false, length = 20)
    private String inviteCode;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    /** XP target participants are competing to reach. */
    @Column(name = "target_xp", nullable = false)
    @Builder.Default
    private Integer targetXp = 100;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "challenge", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<ChallengeParticipant> participants = new ArrayList<>();
}

