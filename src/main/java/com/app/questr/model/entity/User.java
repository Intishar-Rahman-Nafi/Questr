package com.app.questr.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Core user entity.
 * Authentication details (password verification, Spring Security integration)
 * are handled by {@code UserPrincipal} in the security package — keeping
 * this entity a clean domain object with no framework coupling.
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_email",    columnList = "email"),
        @Index(name = "idx_users_username", columnList = "username")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"tasks", "stats", "badges"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    // ── Relationships ─────────────────────────────────────────────────────

    /**
     * Each user has exactly one stats record created at registration.
     * Cascade ALL ensures stats are deleted when the user is deleted.
     */
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL,
              fetch = FetchType.LAZY, orphanRemoval = true)
    private UserStats stats;

    /**
     * All tasks belonging to this user.
     * We use a mutable list and manage it explicitly to avoid accidental
     * full-collection loads.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<Task> tasks = new ArrayList<>();

    /**
     * All badges this user has earned.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<UserBadge> badges = new ArrayList<>();
}

