package com.app.questr.service;

import com.app.questr.model.entity.UserStats;
import com.app.questr.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Midnight cron job that resets streaks for users who missed a day.
 *
 * <p>Runs at 00:00:05 server time (5-second offset to avoid midnight clock skew).
 * Only iterates users who have at least one completed task ({@code lastActivityDate != null}).
 *
 * <p>Logic:
 * <ul>
 *   <li>If {@code lastActivityDate < yesterday} → streak reset to 0.</li>
 *   <li>If {@code lastActivityDate == yesterday} or {@code today} → unchanged.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StreakScheduler {

    private final UserStatsRepository userStatsRepository;

    @Scheduled(cron = "5 0 0 * * *")   // 00:00:05 every day
    @Transactional
    public void resetStaleStreaks() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<UserStats> active = userStatsRepository.findAllByLastActivityDateIsNotNull();

        int resetCount = 0;
        for (UserStats stats : active) {
            if (stats.getLastActivityDate().isBefore(yesterday)) {
                stats.setCurrentStreak(0);
                userStatsRepository.save(stats);
                resetCount++;
            }
        }

        log.info("Streak cron completed: {} streak(s) reset out of {} active user(s)",
                resetCount, active.size());
    }
}

