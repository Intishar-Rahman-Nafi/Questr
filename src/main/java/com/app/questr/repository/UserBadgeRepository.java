package com.app.questr.repository;

import com.app.questr.model.entity.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserBadgeRepository extends JpaRepository<UserBadge, UUID> {

    List<UserBadge> findByUserId(UUID userId);

    /**
     * Idempotency guard: returns true if the user already owns the badge
     * identified by its machine-readable {@code badgeName}.
     * Used in {@code GamificationService.checkAndAwardBadges()}.
     */
    @Query("""
        SELECT COUNT(ub) > 0
        FROM   UserBadge ub
        WHERE  ub.user.id    = :userId
        AND    ub.badge.name = :badgeName
        """)
    boolean hasBadge(@Param("userId")    UUID userId,
                     @Param("badgeName") String badgeName);

    /** Count how many distinct badges a user has earned. */
    long countByUserId(UUID userId);
}

