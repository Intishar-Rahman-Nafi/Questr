package com.app.questr.controller;

import com.app.questr.dto.challenge.ChallengeLeaderboardResponse;
import com.app.questr.dto.challenge.ChallengeResponse;
import com.app.questr.dto.challenge.CreateChallengeRequest;
import com.app.questr.dto.challenge.JoinChallengeRequest;
import com.app.questr.security.UserPrincipal;
import com.app.questr.service.ChallengeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Module 7 — Social Challenges REST API.
 *
 * <p>All endpoints require a valid JWT Bearer token. The currently
 * authenticated user's UUID is resolved from {@link UserPrincipal}.
 *
 * <pre>
 * POST   /api/v1/challenges               — create a new challenge (creator auto-joins)
 * POST   /api/v1/challenges/join          — join an existing challenge by invite code
 * GET    /api/v1/challenges               — list user's challenges (?filter=all|active)
 * GET    /api/v1/challenges/{id}          — get single challenge by id
 * GET    /api/v1/challenges/{id}/leaderboard — challenge leaderboard (participants only)
 * DELETE /api/v1/challenges/{id}          — delete a challenge (creator only)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;

    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/challenges
     *
     * <p>Creates a new challenge. The authenticated user is automatically
     * added as participant #1. Returns 201 Created with the full challenge DTO.
     */
    @PostMapping
    public ResponseEntity<ChallengeResponse> createChallenge(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateChallengeRequest req) {

        ChallengeResponse response = challengeService.createChallenge(principal.getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Join ──────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/challenges/join
     *
     * <p>Join an existing challenge by supplying its 6-char invite code.
     * Returns 200 OK with the challenge DTO (including updated participantCount).
     */
    @PostMapping("/join")
    public ResponseEntity<ChallengeResponse> joinChallenge(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody JoinChallengeRequest req) {

        ChallengeResponse response = challengeService.joinChallenge(principal.getId(), req.inviteCode());
        return ResponseEntity.ok(response);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/challenges[?filter=active|all]
     *
     * <p>Returns the user's challenges. {@code filter=active} (default) returns
     * only challenges whose {@code endDate} is in the future; {@code filter=all}
     * returns every challenge the user has ever joined.
     */
    @GetMapping
    public ResponseEntity<List<ChallengeResponse>> getChallenges(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "active") String filter) {

        List<ChallengeResponse> challenges = "all".equalsIgnoreCase(filter)
                ? challengeService.getUserChallenges(principal.getId())
                : challengeService.getActiveChallenges(principal.getId());

        return ResponseEntity.ok(challenges);
    }

    // ── Get single ────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/challenges/{id}
     *
     * <p>Returns the full challenge DTO for any authenticated user.
     * Used by the detail page to load challenge info before joining.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ChallengeResponse> getChallenge(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                challengeService.getChallenge(id, principal.getId()));
    }

    // ── Leaderboard ───────────────────────────────────────────────────────────

    /**
     * GET /api/v1/challenges/{id}/leaderboard
     *
     * <p>Returns the ranked leaderboard for the given challenge. The user must
     * be a participant; non-participants receive 403 Forbidden.
     */
    @GetMapping("/{id}/leaderboard")
    public ResponseEntity<ChallengeLeaderboardResponse> getLeaderboard(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                challengeService.getChallengeLeaderboard(id, principal.getId()));
    }

    // ── Leave ─────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/challenges/{id}/leave
     *
     * <p>Removes the authenticated user from the challenge's participant list.
     * The creator cannot leave (they must delete the challenge instead).
     */
    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leaveChallenge(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {

        challengeService.leaveChallenge(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * DELETE /api/v1/challenges/{id}
     *
     * <p>Deletes the challenge and all its participant records (cascade).
     * Only the challenge creator may call this endpoint; anyone else receives 403.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChallenge(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {

        challengeService.deleteChallenge(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}

