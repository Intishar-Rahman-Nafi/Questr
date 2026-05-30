package com.app.questr.repository;

import com.app.questr.model.entity.ChallengeParticipant;
import com.app.questr.model.entity.ChallengeParticipantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChallengeParticipantRepository
        extends JpaRepository<ChallengeParticipant, ChallengeParticipantId> {

    /**
     * Leaderboard: all participants for a challenge sorted by XP descending.
     * Called by {@code ChallengeService.getChallengeLeaderboard()}.
     *
     * <p>JOIN FETCH cp.user eagerly loads the User entity in a single query,
     * preventing N+1 SELECT statements when mapping usernames in the service.
     */
    @Query("""
        SELECT cp FROM ChallengeParticipant cp
        JOIN   FETCH cp.user
        WHERE  cp.challenge.id = :challengeId
        ORDER  BY cp.currentXp DESC
        """)
    List<ChallengeParticipant> findLeaderboard(@Param("challengeId") UUID challengeId);

    List<ChallengeParticipant> findByUserId(UUID userId);

    boolean existsByIdChallengeIdAndIdUserId(UUID challengeId, UUID userId);

    /** Total number of participants in a challenge — used in response DTOs. */
    long countByIdChallengeId(UUID challengeId);

    /** Remove a single participant from a challenge (used by leave endpoint). */
    @Modifying
    @Query("""
        DELETE FROM ChallengeParticipant cp
        WHERE  cp.id.challengeId = :challengeId
        AND    cp.id.userId      = :userId
        """)
    int deleteParticipant(@Param("challengeId") UUID challengeId,
                          @Param("userId")      UUID userId);

    /**
     * Atomically increment a participant's XP by {@code xpAmount}.
     * Called from the Kafka XP-event consumer so we avoid a read-modify-write
     * race condition.
     *
     * <p>{@code clearAutomatically = true} evicts the affected entity from the
     * first-level (EntityManager) cache so subsequent {@code findById} calls
     * return the freshly updated value rather than a stale cached copy.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE ChallengeParticipant cp
        SET    cp.currentXp = cp.currentXp + :xpAmount
        WHERE  cp.id.challengeId = :challengeId
        AND    cp.id.userId      = :userId
        """)
    int addXp(@Param("challengeId") UUID challengeId,
              @Param("userId")      UUID userId,
              @Param("xpAmount")    int xpAmount);
}

