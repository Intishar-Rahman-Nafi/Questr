package com.app.questr.model.entity;

import com.app.questr.model.enums.BadgeType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * A badge definition (seed data — inserted by Flyway migration V6).
 * Badges are shared across all users; {@link UserBadge} records which
 * users have earned which badges.
 */
@Entity
@Table(
    name = "badges",
    indexes = {
        @Index(name = "idx_badges_name", columnList = "name")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString
public class Badge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** Unique machine-readable name, e.g. "FIRST_TASK" or "STREAK_7". */
    @Column(unique = true, nullable = false, length = 100)
    private String name;

    /** Human-readable display name, e.g. "7-Day Streak Master". */
    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Emoji or URL for the badge icon, e.g. "🔥" or "/icons/streak7.png". */
    @Column(name = "icon_url", length = 255)
    private String iconUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "badge_type", nullable = false, length = 50)
    private BadgeType badgeType;

    /**
     * JSON string describing unlock criteria, e.g.:
     * {@code {"type":"STREAK","days":7}}
     * Parsed in {@code GamificationService.checkAndAwardBadges()}.
     */
    @Column(columnDefinition = "TEXT")
    private String criteria;

    /** Bonus XP awarded to the user when this badge is unlocked. */
    @Column(name = "reward_xp", nullable = false)
    @Builder.Default
    private Integer rewardXp = 0;
}

