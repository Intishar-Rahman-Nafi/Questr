package com.app.questr.repository;

import com.app.questr.model.entity.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {

    Optional<Challenge> findByInviteCode(String inviteCode);

    List<Challenge> findByCreatedById(UUID userId);

    /**
     * Find all challenges in which the given user is a participant
     * (including challenges they created).
     */
    @Query("""
        SELECT c FROM Challenge c
        JOIN   c.participants cp
        WHERE  cp.user.id = :userId
        """)
    List<Challenge> findAllByParticipantUserId(@Param("userId") UUID userId);

    /**
     * Active challenges for a user — end_date is in the future.
     */
    @Query("""
        SELECT c FROM Challenge c
        JOIN   c.participants cp
        WHERE  cp.user.id = :userId
        AND    c.endDate  > :now
        ORDER  BY c.endDate ASC
        """)
    List<Challenge> findActiveByParticipantUserId(
        @Param("userId") UUID userId,
        @Param("now")    LocalDateTime now);

    boolean existsByInviteCode(String inviteCode);
}

