package com.app.questr.service;

import com.app.questr.event.XPUpdateEvent;
import com.app.questr.model.entity.Badge;
import com.app.questr.model.entity.User;
import com.app.questr.model.entity.UserBadge;
import com.app.questr.model.entity.UserStats;
import com.app.questr.model.enums.BadgeType;
import com.app.questr.repository.BadgeRepository;
import com.app.questr.repository.UserBadgeRepository;
import com.app.questr.repository.UserStatsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GamificationService}.
 *
 * All dependencies are Mockito mocks — no Spring context, no database.
 * Kafka and Redis failures are silently swallowed, so those mocks simply
 * do nothing by default.
 *
 * LENIENT strictness is used because {@code @BeforeEach} sets up shared stubs
 * (repo finders, saves) that are not needed by static-method tests like
 * {@code calculateLevel_*}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GamificationServiceTest {

    @Mock UserStatsRepository                  userStatsRepository;
    @Mock BadgeRepository                      badgeRepository;
    @Mock UserBadgeRepository                  userBadgeRepository;
    @Mock KafkaTemplate<String, XPUpdateEvent> kafkaTemplate;
    @Mock RedisTemplate<String, Object>        redisTemplate;
    @Mock ValueOperations<String, Object>      valueOps;

    @InjectMocks GamificationService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID   userId;
    private User   user;
    private UserStats stats;

    @BeforeEach
    void setUp() {
        // Inject real ObjectMapper (Mockito @InjectMocks doesn't handle it)
        try {
            var f = GamificationService.class.getDeclaredField("objectMapper");
            f.setAccessible(true);
            f.set(service, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        userId = UUID.randomUUID();
        user   = User.builder().id(userId).username("hero").email("h@q.com").build();
        stats  = UserStats.builder()
                .id(UUID.randomUUID())
                .user(user)
                .totalXp(0)
                .level(1)
                .currentStreak(0)
                .longestStreak(0)
                .tasksCompleted(0)
                .build();

        when(userStatsRepository.findByUserId(userId)).thenReturn(Optional.of(stats));
        when(userStatsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(badgeRepository.findAll()).thenReturn(List.of()); // no badges by default
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── awardXP — XP & level ─────────────────────────────────────────────────

    @Nested
    @DisplayName("awardXP — XP and level")
    class AwardXpTests {

        @Test
        @DisplayName("adds xp to totalXp")
        void addsXp() {
            service.awardXP(userId, 20);

            assertThat(stats.getTotalXp()).isEqualTo(20);
            verify(userStatsRepository, atLeastOnce()).save(stats);
        }

        @Test
        @DisplayName("increments tasksCompleted by 1")
        void incrementsTasksCompleted() {
            stats = UserStats.builder().id(UUID.randomUUID()).user(user)
                    .totalXp(0).level(1).tasksCompleted(4).currentStreak(0).longestStreak(0).build();
            when(userStatsRepository.findByUserId(userId)).thenReturn(Optional.of(stats));

            service.awardXP(userId, 10);

            assertThat(stats.getTasksCompleted()).isEqualTo(5);
        }

        @Test
        @DisplayName("level does not change below threshold")
        void noLevelUpBelowThreshold() {
            service.awardXP(userId, 10); // 10 XP → still level 1
            assertThat(stats.getLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("level increases at threshold (375 XP + 5 = 380 → level 2)")
        void levelUpAtThreshold() {
            stats = UserStats.builder().id(UUID.randomUUID()).user(user)
                    .totalXp(375).level(1).currentStreak(1)
                    .longestStreak(1).tasksCompleted(10).build();
            when(userStatsRepository.findByUserId(userId)).thenReturn(Optional.of(stats));

            service.awardXP(userId, 5); // 380 XP → level 2

            assertThat(stats.getLevel()).isGreaterThanOrEqualTo(2);
        }
    }

    // ── awardXP — streak ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("awardXP — streak logic")
    class StreakTests {

        @Test
        @DisplayName("first task sets streak to 1")
        void firstTask_streak1() {
            service.awardXP(userId, 5);
            assertThat(stats.getCurrentStreak()).isEqualTo(1);
            assertThat(stats.getLastActivityDate()).isEqualTo(LocalDate.now());
        }

        @Test
        @DisplayName("same-day second task does not increment streak")
        void sameDayNoIncrement() {
            stats.setCurrentStreak(3);
            stats.setLastActivityDate(LocalDate.now());

            service.awardXP(userId, 5);

            assertThat(stats.getCurrentStreak()).isEqualTo(3); // unchanged
        }

        @Test
        @DisplayName("consecutive day increments streak")
        void consecutiveDayIncrement() {
            stats.setCurrentStreak(4);
            stats.setLastActivityDate(LocalDate.now().minusDays(1));

            service.awardXP(userId, 5);

            assertThat(stats.getCurrentStreak()).isEqualTo(5);
        }

        @Test
        @DisplayName("gap of 2+ days resets streak to 1")
        void gapResetsStreak() {
            stats.setCurrentStreak(10);
            stats.setLastActivityDate(LocalDate.now().minusDays(3));

            service.awardXP(userId, 5);

            assertThat(stats.getCurrentStreak()).isEqualTo(1);
        }

        @Test
        @DisplayName("longestStreak is updated when current exceeds previous record")
        void longestStreakUpdated() {
            stats.setCurrentStreak(7);
            stats.setLongestStreak(7);
            stats.setLastActivityDate(LocalDate.now().minusDays(1));

            service.awardXP(userId, 5);

            assertThat(stats.getCurrentStreak()).isEqualTo(8);
            assertThat(stats.getLongestStreak()).isEqualTo(8);
        }
    }

    // ── calculateLevel — formula ──────────────────────────────────────────────

    @Nested
    @DisplayName("calculateLevel — formula correctness")
    class CalculateLevelTests {

        @Test @DisplayName("0 XP → level 1")   void zero()    { assertThat(GamificationService.calculateLevel(0)).isEqualTo(1); }
        @Test @DisplayName("negative XP → level 1") void negative() { assertThat(GamificationService.calculateLevel(-50)).isEqualTo(1); }
        @Test @DisplayName("1 XP → level 1")   void oneXp()   { assertThat(GamificationService.calculateLevel(1)).isEqualTo(1); }
        @Test @DisplayName("379 XP → level 1") void below380() { assertThat(GamificationService.calculateLevel(379)).isEqualTo(1); }
        @Test @DisplayName("380 XP → level 2") void at380()   { assertThat(GamificationService.calculateLevel(380)).isEqualTo(2); }
        @Test @DisplayName("900 XP → level 3") void at900()   { assertThat(GamificationService.calculateLevel(900)).isEqualTo(3); }
    }

    // ── checkAndAwardBadges ───────────────────────────────────────────────────

    @Nested
    @DisplayName("checkAndAwardBadges — badge award logic")
    class BadgeTests {

        @Test
        @DisplayName("FIRST_TASK badge is awarded when tasksCompleted == 1")
        void firstTaskBadgeAwarded() {
            stats.setTasksCompleted(1);
            Badge firstTask = Badge.builder()
                    .id(UUID.randomUUID())
                    .name("FIRST_TASK")
                    .displayName("Quest Begins")
                    .description("First task done")
                    .badgeType(BadgeType.TASK_COUNT)
                    .criteria("{\"type\":\"TASK_COUNT\",\"count\":1}")
                    .rewardXp(0)
                    .build();

            when(badgeRepository.findAll()).thenReturn(List.of(firstTask));
            when(userBadgeRepository.hasBadge(userId, "FIRST_TASK")).thenReturn(false);
            when(userBadgeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.checkAndAwardBadges(stats);

            ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
            verify(userBadgeRepository).save(captor.capture());
            assertThat(captor.getValue().getBadge().getName()).isEqualTo("FIRST_TASK");
        }

        @Test
        @DisplayName("badge is NOT awarded if user already has it (idempotency)")
        void idempotent_alreadyEarned() {
            stats.setTasksCompleted(1);
            Badge firstTask = Badge.builder()
                    .id(UUID.randomUUID()).name("FIRST_TASK")
                    .badgeType(BadgeType.TASK_COUNT)
                    .criteria("{\"type\":\"TASK_COUNT\",\"count\":1}")
                    .rewardXp(0).build();

            when(badgeRepository.findAll()).thenReturn(List.of(firstTask));
            when(userBadgeRepository.hasBadge(userId, "FIRST_TASK")).thenReturn(true); // already earned

            service.checkAndAwardBadges(stats);

            verify(userBadgeRepository, never()).save(any());
        }

        @Test
        @DisplayName("STREAK_3 badge awarded when streak >= 3")
        void streak3BadgeAwarded() {
            stats.setCurrentStreak(3);
            Badge streak3 = Badge.builder()
                    .id(UUID.randomUUID()).name("STREAK_3")
                    .displayName("3-Day Warrior")
                    .badgeType(BadgeType.STREAK)
                    .criteria("{\"type\":\"STREAK\",\"days\":3}")
                    .rewardXp(50).build();

            when(badgeRepository.findAll()).thenReturn(List.of(streak3));
            when(userBadgeRepository.hasBadge(userId, "STREAK_3")).thenReturn(false);
            when(userBadgeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.checkAndAwardBadges(stats);

            verify(userBadgeRepository).save(argThat(ub -> ub.getBadge().getName().equals("STREAK_3")));
            // Bonus XP should have been applied
            assertThat(stats.getTotalXp()).isEqualTo(50);
        }

        @Test
        @DisplayName("XP badge awarded and bonus XP is added")
        void xpBadgeAwardedWithBonusXp() {
            stats.setTotalXp(1000);
            Badge xp1000 = Badge.builder()
                    .id(UUID.randomUUID()).name("XP_1000")
                    .displayName("XP Adventurer")
                    .badgeType(BadgeType.XP_MILESTONE)
                    .criteria("{\"type\":\"XP\",\"threshold\":1000}")
                    .rewardXp(100).build();

            when(badgeRepository.findAll()).thenReturn(List.of(xp1000));
            when(userBadgeRepository.hasBadge(userId, "XP_1000")).thenReturn(false);
            when(userBadgeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.checkAndAwardBadges(stats);

            assertThat(stats.getTotalXp()).isEqualTo(1100); // 1000 + 100 bonus
        }

        @Test
        @DisplayName("badge with malformed criteria is skipped gracefully")
        void malformedCriteriaSkipped() {
            Badge broken = Badge.builder()
                    .id(UUID.randomUUID()).name("BROKEN")
                    .badgeType(BadgeType.SPECIAL)
                    .criteria("not-json")
                    .rewardXp(0).build();

            when(badgeRepository.findAll()).thenReturn(List.of(broken));
            when(userBadgeRepository.hasBadge(userId, "BROKEN")).thenReturn(false);

            // Should not throw
            service.checkAndAwardBadges(stats);

            verify(userBadgeRepository, never()).save(any());
        }
    }

    // ── Kafka & Redis best-effort ─────────────────────────────────────────────

    @Test
    @DisplayName("Kafka failure does not abort XP award")
    void kafkaFailure_doesNotAbort() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenThrow(new RuntimeException("Kafka down"));

        // Should not throw despite Kafka failure
        service.awardXP(userId, 10);

        assertThat(stats.getTotalXp()).isEqualTo(10);
    }

    @Test
    @DisplayName("Redis failure does not abort XP award")
    void redisFailure_doesNotAbort() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

        service.awardXP(userId, 10);

        assertThat(stats.getTotalXp()).isEqualTo(10);
    }
}






