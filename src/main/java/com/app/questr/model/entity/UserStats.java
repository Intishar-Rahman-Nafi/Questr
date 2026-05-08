package com.app.questr.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Gamification stats for a user.
 * One-to-one with {@link User}; created atomically during registration.
 *
 * <p>Level formula (same as plan):
 * {@code level = floor((sqrt(100 * totalXp + 25) + 5) / 100)}
 * which means each level requires progressively more XP.
 */
@Entity
@Table(
    name = "user_stats",
    indexes = {
        @Index(name = "idx_user_stats_user_id", columnList = "user_id"),
        @Index(name = "idx_user_stats_total_xp", columnList = "total_xp")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "user")
public class UserStats {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "total_xp", nullable = false)
    @Builder.Default
    private Integer totalXp = 0;

    @Column(name = "level", nullable = false)
    @Builder.Default
    private Integer level = 1;

    @Column(name = "current_streak", nullable = false)
    @Builder.Default
    private Integer currentStreak = 0;

    @Column(name = "longest_streak", nullable = false)
    @Builder.Default
    private Integer longestStreak = 0;

    @Column(name = "tasks_completed", nullable = false)
    @Builder.Default
    private Integer tasksCompleted = 0;

    /**
     * Date of most recent task completion — used by the midnight streak cron
     * to decide whether to increment or reset the streak.
     */
    @Column(name = "last_activity_date")
    private LocalDate lastActivityDate;
}

