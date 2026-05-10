package com.app.questr.service;

import com.app.questr.exception.ResourceNotFoundException;
import com.app.questr.model.entity.UserStats;
import com.app.questr.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Core gamification engine.
 * Level formula: level = max(1, floor((sqrt(100 * totalXp + 25) + 5) / 100))
 * Module 5 will layer Kafka + Redis on top of the same public surface.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class GamificationService {

    private final UserStatsRepository userStatsRepository;

    public void awardXP(UUID userId, int xpAmount) {
        UserStats stats = userStatsRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("UserStats", userId));

        // 1. Add XP and recalculate level
        int newTotal = stats.getTotalXp() + xpAmount;
        stats.setTotalXp(newTotal);
        int newLevel = calculateLevel(newTotal);
        if (newLevel > stats.getLevel()) {
            log.info("User {} leveled up: {} -> {}", userId, stats.getLevel(), newLevel);
            stats.setLevel(newLevel);
        }

        // 2. Increment completed counter
        stats.setTasksCompleted(stats.getTasksCompleted() + 1);

        // 3. Update streak (once per calendar day)
        LocalDate today = LocalDate.now();
        LocalDate last  = stats.getLastActivityDate();
        if (last == null) {
            stats.setCurrentStreak(1);
        } else if (last.equals(today)) {
            log.debug("Already active today — no streak change for user {}", userId);
        } else if (last.equals(today.minusDays(1))) {
            stats.setCurrentStreak(stats.getCurrentStreak() + 1);
        } else {
            stats.setCurrentStreak(1);
        }

        if (stats.getCurrentStreak() > stats.getLongestStreak()) {
            stats.setLongestStreak(stats.getCurrentStreak());
        }
        stats.setLastActivityDate(today);

        userStatsRepository.save(stats);
        log.info("XP +{} user={} total={} level={} streak={}",
                xpAmount, userId, stats.getTotalXp(), stats.getLevel(), stats.getCurrentStreak());
    }

    /** Public so callers and tests can compute expected levels. */
    public static int calculateLevel(int totalXp) {
        if (totalXp <= 0) return 1;
        return Math.max(1, (int) Math.floor((Math.sqrt(100.0 * totalXp + 25) + 5) / 100));
    }
}



