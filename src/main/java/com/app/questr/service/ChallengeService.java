package com.app.questr.service;

import com.app.questr.dto.challenge.ChallengeLeaderboardEntry;
import com.app.questr.dto.challenge.ChallengeLeaderboardResponse;
import com.app.questr.dto.challenge.ChallengeResponse;
import com.app.questr.dto.challenge.CreateChallengeRequest;
import com.app.questr.exception.ApiException;
import com.app.questr.exception.ResourceNotFoundException;
import com.app.questr.model.entity.Challenge;
import com.app.questr.model.entity.ChallengeParticipant;
import com.app.questr.model.entity.ChallengeParticipantId;
import com.app.questr.model.entity.User;
import com.app.questr.repository.ChallengeParticipantRepository;
import com.app.questr.repository.ChallengeRepository;
import com.app.questr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Module 7 — Social Challenges service.
 *
 * <p>Encapsulates all business logic for challenge lifecycle:
 * <ul>
 *   <li>Creating a challenge (creator auto-joins)</li>
 *   <li>Joining by invite code (idempotency check)</li>
 *   <li>Listing a user's challenges (all or active-only)</li>
 *   <li>Fetching the leaderboard (participants only)</li>
 *   <li>Deleting a challenge (creator only)</li>
 * </ul>
 *
 * <p>Invite codes are 6-char uppercase alphanumeric, generated with
 * {@link SecureRandom} and retried until unique.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ChallengeService {

    private final ChallengeRepository            challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final UserRepository                 userRepository;

    private static final SecureRandom RANDOM     = new SecureRandom();
    private static final String       CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int          CODE_LEN   = 6;

    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * Create a new challenge. The creator automatically becomes participant #1.
     *
     * @throws ApiException 400 if {@code endDate} is not strictly after {@code startDate}
     */
    public ChallengeResponse createChallenge(UUID userId, CreateChallengeRequest req) {
        User user = resolveUser(userId);

        if (!req.startDate().isBefore(req.endDate())) {
            throw new ApiException("endDate must be strictly after startDate", HttpStatus.BAD_REQUEST);
        }

        String inviteCode = generateUniqueInviteCode();

        Challenge challenge = Challenge.builder()
                .name(req.name())
                .description(req.description())
                .inviteCode(inviteCode)
                .startDate(req.startDate())
                .endDate(req.endDate())
                .targetXp(req.targetXp() != null ? req.targetXp() : 100)
                .createdBy(user)
                .build();

        challenge = challengeRepository.save(challenge);

        // Auto-join the creator
        ChallengeParticipant cp = ChallengeParticipant.builder()
                .id(new ChallengeParticipantId(challenge.getId(), userId))
                .challenge(challenge)
                .user(user)
                .build();
        participantRepository.save(cp);

        log.info("Challenge '{}' created by user {} with code {}", challenge.getName(), userId, inviteCode);
        return toResponse(challenge, userId);
    }

    // ── Join ──────────────────────────────────────────────────────────────────

    /**
     * Join an existing challenge by its invite code.
     *
     * @throws ApiException 404 if no challenge matches {@code inviteCode}
     * @throws ApiException 409 if the user is already a participant
     */
    public ChallengeResponse joinChallenge(UUID userId, String inviteCode) {
        Challenge challenge = challengeRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new ApiException(
                        "No challenge found with invite code: " + inviteCode, HttpStatus.NOT_FOUND));

        if (participantRepository.existsByIdChallengeIdAndIdUserId(challenge.getId(), userId)) {
            throw new ApiException(
                    "You are already a participant in this challenge", HttpStatus.CONFLICT);
        }

        User user = resolveUser(userId);

        ChallengeParticipant cp = ChallengeParticipant.builder()
                .id(new ChallengeParticipantId(challenge.getId(), userId))
                .challenge(challenge)
                .user(user)
                .build();
        participantRepository.save(cp);

        log.info("User {} joined challenge '{}' (code: {})", userId, challenge.getName(), inviteCode);
        return toResponse(challenge, userId);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    /**
     * Return ALL challenges the user participates in (active, past, and future).
     */
    @Transactional(readOnly = true)
    public List<ChallengeResponse> getUserChallenges(UUID userId) {
        return challengeRepository.findAllByParticipantUserId(userId)
                .stream()
                .map(c -> toResponse(c, userId))
                .collect(Collectors.toList());
    }

    /**
     * Return only ACTIVE challenges (startDate ≤ now ≤ endDate) for the user.
     */
    @Transactional(readOnly = true)
    public List<ChallengeResponse> getActiveChallenges(UUID userId) {
        return challengeRepository.findActiveByParticipantUserId(userId, LocalDateTime.now())
                .stream()
                .map(c -> toResponse(c, userId))
                .collect(Collectors.toList());
    }

    // ── Get single ───────────────────────────────────────────────────────────

    /**
     * Return the full challenge DTO for any authenticated user.
     *
     * @throws ResourceNotFoundException 404 if the challenge does not exist
     */
    @Transactional(readOnly = true)
    public ChallengeResponse getChallenge(UUID challengeId, UUID requestingUserId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResourceNotFoundException("Challenge", challengeId));
        return toResponse(challenge, requestingUserId);
    }

    // ── Leaderboard ──────────────────────────────────────────────────────────

    /**
     * Fetch the leaderboard for a challenge.
     *
     * <p>Only participants may see the leaderboard — non-participants receive 403.
     *
     * @throws ResourceNotFoundException 404 if the challenge does not exist
     * @throws ApiException              403 if the requesting user is not a participant
     */
    @Transactional(readOnly = true)
    public ChallengeLeaderboardResponse getChallengeLeaderboard(UUID challengeId, UUID requestingUserId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResourceNotFoundException("Challenge", challengeId));

        if (!participantRepository.existsByIdChallengeIdAndIdUserId(challengeId, requestingUserId)) {
            throw new ApiException(
                    "You must be a participant to view the leaderboard", HttpStatus.FORBIDDEN);
        }

        List<ChallengeParticipant> participants = participantRepository.findLeaderboard(challengeId);

        List<ChallengeLeaderboardEntry> entries = new ArrayList<>(participants.size());
        for (int i = 0; i < participants.size(); i++) {
            ChallengeParticipant cp = participants.get(i);
            entries.add(new ChallengeLeaderboardEntry(
                    i + 1,
                    cp.getUser().getId(),
                    cp.getUser().getUsername(),
                    cp.getCurrentXp(),
                    cp.getJoinedAt()));
        }

        return new ChallengeLeaderboardResponse(
                challengeId,
                challenge.getName(),
                challenge.getTargetXp(),
                entries);
    }

    // ── Leave ─────────────────────────────────────────────────────────────────

    /**
     * Leave a challenge.
     *
     * <p>The creator may not leave their own challenge (they must delete it instead).
     *
     * @throws ResourceNotFoundException 404 if the challenge does not exist
     * @throws ApiException              403 if the user is the creator
     * @throws ApiException              409 if the user is not a participant
     */
    public void leaveChallenge(UUID challengeId, UUID userId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResourceNotFoundException("Challenge", challengeId));

        if (challenge.getCreatedBy().getId().equals(userId)) {
            throw new ApiException(
                    "The creator cannot leave their own challenge. Delete it instead.", HttpStatus.FORBIDDEN);
        }

        int deleted = participantRepository.deleteParticipant(challengeId, userId);
        if (deleted == 0) {
            throw new ApiException("You are not a participant in this challenge", HttpStatus.CONFLICT);
        }

        log.info("User {} left challenge '{}'", userId, challenge.getName());
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Delete a challenge (and all its participants via cascade).
     *
     * @throws ResourceNotFoundException 404 if the challenge does not exist
     * @throws ApiException              403 if the requesting user is not the creator
     */
    public void deleteChallenge(UUID challengeId, UUID userId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResourceNotFoundException("Challenge", challengeId));

        if (!challenge.getCreatedBy().getId().equals(userId)) {
            throw new ApiException(
                    "Only the challenge creator can delete this challenge", HttpStatus.FORBIDDEN);
        }

        challengeRepository.delete(challenge);
        log.info("Challenge {} deleted by creator {}", challengeId, userId);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private User resolveUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private Challenge reload(UUID challengeId) {
        return challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResourceNotFoundException("Challenge", challengeId));
    }

    private ChallengeResponse toResponse(Challenge c, UUID requestingUserId) {
        LocalDateTime now    = LocalDateTime.now();
        boolean       active = c.getStartDate().isBefore(now) && c.getEndDate().isAfter(now);
        boolean       isCreator = c.getCreatedBy().getId().equals(requestingUserId);

        // Use a count query instead of c.getParticipants().size() to avoid the
        // Hibernate L1-cache issue where the in-memory participants collection
        // may be stale after a separate participantRepository.save() call.
        int participantCount = (int) participantRepository.countByIdChallengeId(c.getId());

        return new ChallengeResponse(
                c.getId(),
                c.getName(),
                c.getDescription(),
                c.getInviteCode(),
                c.getStartDate(),
                c.getEndDate(),
                c.getTargetXp(),
                c.getCreatedBy().getId(),
                c.getCreatedBy().getUsername(),
                c.getCreatedAt(),
                participantCount,
                active,
                isCreator);
    }

    /**
     * Generate a unique 6-char uppercase alphanumeric invite code.
     * Loops until a code not already in the DB is found (collision is astronomically rare).
     */
    private String generateUniqueInviteCode() {
        String code;
        do {
            code = IntStream.range(0, CODE_LEN)
                    .mapToObj(i -> String.valueOf(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length()))))
                    .collect(Collectors.joining());
        } while (challengeRepository.existsByInviteCode(code));
        return code;
    }
}




