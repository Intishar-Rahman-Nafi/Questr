package com.app.questr.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records that a specific {@link User} has earned a specific {@link Badge}.
 * The pair (user_id, badge_id) has a unique constraint — earning the same
 * badge twice is prevented both here and in {@code GamificationService}.
 */
@Entity
@Table(
    name = "user_badges",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_user_badge",
            columnNames = {"user_id", "badge_id"}
        )
    },
    indexes = {
        @Index(name = "idx_user_badges_user_id", columnList = "user_id")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"user", "badge"})
public class UserBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    // Breaks the User.badges <-> UserBadge.user cycle for Jackson.
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)   // Badge is tiny; eager is fine here
    @JoinColumn(name = "badge_id", nullable = false)
    private Badge badge;

    @CreationTimestamp
    @Column(name = "earned_at", nullable = false, updatable = false)
    private LocalDateTime earnedAt;
}

