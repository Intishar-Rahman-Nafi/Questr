package com.app.questr.repository;
import com.app.questr.model.entity.*;
import com.app.questr.model.enums.BadgeType;
import com.app.questr.model.enums.TaskCategory;
import com.app.questr.model.enums.TaskPriority;
import com.app.questr.model.projection.CategoryBreakdownProjection;
import com.app.questr.model.projection.DailyCompletionProjection;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Module 2 — Repository Integration Tests
 * Uses real PostgreSQL 15 via Testcontainers so Flyway migrations V1-V6
 * run exactly as in production. Each test is @Transactional (rolled back),
 * but Flyway badge seed data is committed once at context start.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Sql(scripts = "/test-badge-data.sql", executionPhase = ExecutionPhase.BEFORE_TEST_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RepositoryIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine");
    @Autowired UserRepository                 userRepository;
    @Autowired TaskRepository                 taskRepository;
    @Autowired UserStatsRepository            statsRepository;
    @Autowired BadgeRepository                badgeRepository;
    @Autowired UserBadgeRepository            userBadgeRepository;
    @Autowired ChallengeRepository            challengeRepository;
    @Autowired ChallengeParticipantRepository participantRepository;
    private User savedUser;
    @BeforeEach
    void setUp() {
        savedUser = userRepository.saveAndFlush(User.builder()
            .username("testuser").email("test@questr.io")
            .passwordHash("$2a$10$hashed").build());
        statsRepository.saveAndFlush(UserStats.builder().user(savedUser).build());
    }
    // ─────────────────────────────── USER ────────────────────────────────
    @Test @Order(1)
    @DisplayName("User: findByEmail returns correct user")
    void user_findByEmail() {
        Optional<User> found = userRepository.findByEmail("test@questr.io");
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser");
    }
    @Test @Order(2)
    @DisplayName("User: existsByEmail and existsByUsername")
    void user_exists() {
        assertThat(userRepository.existsByEmail("test@questr.io")).isTrue();
        assertThat(userRepository.existsByEmail("ghost@questr.io")).isFalse();
        assertThat(userRepository.existsByUsername("testuser")).isTrue();
        assertThat(userRepository.existsByUsername("nobody")).isFalse();
    }
    @Test @Order(3)
    @DisplayName("User: createdAt auto-populated by @CreationTimestamp")
    void user_createdAtPopulated() {
        assertThat(savedUser.getCreatedAt()).isNotNull();
    }
    // ─────────────────────────── USER STATS ──────────────────────────────
    @Test @Order(4)
    @DisplayName("UserStats: findByUserId returns correct defaults")
    void stats_defaults() {
        UserStats stats = statsRepository.findByUserId(savedUser.getId()).orElseThrow();
        assertThat(stats.getTotalXp()).isEqualTo(0);
        assertThat(stats.getLevel()).isEqualTo(1);
        assertThat(stats.getCurrentStreak()).isEqualTo(0);
    }
    @Test @Order(5)
    @DisplayName("UserStats: can update XP, level, streak, lastActivityDate")
    void stats_update() {
        UserStats stats = statsRepository.findByUserId(savedUser.getId()).orElseThrow();
        stats.setTotalXp(250);
        stats.setLevel(2);
        stats.setCurrentStreak(5);
        stats.setLastActivityDate(LocalDate.now());
        statsRepository.saveAndFlush(stats);
        UserStats reloaded = statsRepository.findByUserId(savedUser.getId()).orElseThrow();
        assertThat(reloaded.getTotalXp()).isEqualTo(250);
        assertThat(reloaded.getLevel()).isEqualTo(2);
        assertThat(reloaded.getLastActivityDate()).isEqualTo(LocalDate.now());
    }
    // ────────────────────────────── TASKS ────────────────────────────────
    @Test @Order(6)
    @DisplayName("Task: save and findByUserId")
    void task_saveAndFind() {
        taskRepository.saveAndFlush(Task.builder().user(savedUser).title("Write tests")
            .category(TaskCategory.DEV).priority(TaskPriority.HIGH).xpValue(20).build());
        List<Task> tasks = taskRepository.findByUserId(savedUser.getId());
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getCategory()).isEqualTo(TaskCategory.DEV);
        assertThat(tasks.get(0).getPriority()).isEqualTo(TaskPriority.HIGH);
    }
    @Test @Order(7)
    @DisplayName("Task: countByUserId and countByUserIdAndCompletedTrue")
    void task_counts() {
        saveTask("T1", true,  TaskCategory.WORK,   TaskPriority.HIGH);
        saveTask("T2", true,  TaskCategory.WORK,   TaskPriority.MEDIUM);
        saveTask("T3", false, TaskCategory.HEALTH, TaskPriority.LOW);
        assertThat(taskRepository.countByUserId(savedUser.getId())).isEqualTo(3);
        assertThat(taskRepository.countByUserIdAndCompletedTrue(savedUser.getId())).isEqualTo(2);
    }
    @Test @Order(8)
    @DisplayName("Task: getWeeklyCompletions aggregates by day")
    void task_weeklyCompletions() {
        LocalDateTime monday = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        saveCompletedTask("A", TaskCategory.WORK, monday.plusHours(9));
        saveCompletedTask("B", TaskCategory.DEV,  monday.plusHours(11));
        saveTask("Pending", false, TaskCategory.WORK, TaskPriority.LOW);
        List<DailyCompletionProjection> result =
            taskRepository.getWeeklyCompletions(savedUser.getId(), monday);
        long total = result.stream().mapToLong(DailyCompletionProjection::getCount).sum();
        assertThat(total).isEqualTo(2);
    }
    @Test @Order(9)
    @DisplayName("Task: getCategoryBreakdown groups completed tasks only")
    void task_categoryBreakdown() {
        LocalDateTime now = LocalDateTime.now();
        saveCompletedTask("W1", TaskCategory.WORK,   now);
        saveCompletedTask("W2", TaskCategory.WORK,   now);
        saveCompletedTask("H1", TaskCategory.HEALTH, now);
        saveTask("Pending",     false, TaskCategory.DEV, TaskPriority.LOW);
        List<CategoryBreakdownProjection> breakdown =
            taskRepository.getCategoryBreakdown(savedUser.getId());
        assertThat(breakdown).hasSize(2);
        breakdown.stream().filter(b -> "WORK".equals(b.getCategory())).findFirst()
            .ifPresent(w -> assertThat(w.getCount()).isEqualTo(2L));
    }
    // ────────────────────────────── BADGES ───────────────────────────────
    @Test @Order(10)
    @DisplayName("Badge: Flyway V6 seeded at least 18 badges")
    void badge_seedCount() {
        assertThat(badgeRepository.count()).isGreaterThanOrEqualTo(18);
    }
    @Test @Order(11)
    @DisplayName("Badge: findByName returns correct badge with rewardXp")
    void badge_findByName() {
        Badge badge = badgeRepository.findByName("FIRST_TASK").orElseThrow();
        assertThat(badge.getBadgeType()).isEqualTo(BadgeType.TASK_COUNT);
        assertThat(badge.getRewardXp()).isEqualTo(0);
        assertThat(badge.getDisplayName()).isEqualTo("Quest Begins");
    }
    @Test @Order(12)
    @DisplayName("Badge: findByBadgeType returns correct subset sizes")
    void badge_byType() {
        assertThat(badgeRepository.findByBadgeType(BadgeType.STREAK)).hasSize(6);
        assertThat(badgeRepository.findByBadgeType(BadgeType.XP_MILESTONE)).hasSize(4);
        assertThat(badgeRepository.findByBadgeType(BadgeType.TASK_COUNT)).hasSize(4);
        assertThat(badgeRepository.findByBadgeType(BadgeType.SPECIAL)).hasSize(4);
    }
    @Test @Order(13)
    @DisplayName("UserBadge: award badge and verify hasBadge idempotency guard")
    void userBadge_award() {
        Badge badge = badgeRepository.findByName("FIRST_TASK").orElseThrow();
        assertThat(userBadgeRepository.hasBadge(savedUser.getId(), "FIRST_TASK")).isFalse();
        userBadgeRepository.saveAndFlush(UserBadge.builder().user(savedUser).badge(badge).build());
        assertThat(userBadgeRepository.hasBadge(savedUser.getId(), "FIRST_TASK")).isTrue();
        assertThat(userBadgeRepository.hasBadge(savedUser.getId(), "STREAK_7")).isFalse();
        assertThat(userBadgeRepository.countByUserId(savedUser.getId())).isEqualTo(1);
    }
    // ──────────────────────────── CHALLENGES ─────────────────────────────
    @Test @Order(14)
    @DisplayName("Challenge: save and findByInviteCode")
    void challenge_saveAndFind() {
        challengeRepository.saveAndFlush(buildChallenge("INV001"));
        assertThat(challengeRepository.findByInviteCode("INV001")).isPresent();
        assertThat(challengeRepository.existsByInviteCode("NOPE99")).isFalse();
    }
    @Test @Order(15)
    @DisplayName("Challenge: findActiveByParticipantUserId filters expired")
    void challenge_findActive() {
        Challenge active  = buildChallenge("ACT001");
        Challenge expired = buildChallenge("EXP002");
        expired.setEndDate(LocalDateTime.now().minusDays(1));
        challengeRepository.saveAndFlush(active);
        challengeRepository.saveAndFlush(expired);
        addParticipant(active,  savedUser);
        addParticipant(expired, savedUser);
        List<Challenge> result = challengeRepository
            .findActiveByParticipantUserId(savedUser.getId(), LocalDateTime.now());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getInviteCode()).isEqualTo("ACT001");
    }
    @Test @Order(16)
    @DisplayName("ChallengeParticipant: addXp atomically increments currentXp")
    void participant_addXp() {
        Challenge c = challengeRepository.saveAndFlush(buildChallenge("XP001"));
        addParticipant(c, savedUser);
        participantRepository.addXp(c.getId(), savedUser.getId(), 50);
        participantRepository.addXp(c.getId(), savedUser.getId(), 25);
        ChallengeParticipant cp = participantRepository
            .findById(new ChallengeParticipantId(c.getId(), savedUser.getId())).orElseThrow();
        assertThat(cp.getCurrentXp()).isEqualTo(75);
    }
    @Test @Order(17)
    @DisplayName("ChallengeParticipant: leaderboard ordered by XP descending")
    void participant_leaderboard() {
        User u2 = userRepository.saveAndFlush(
            User.builder().username("u2").email("u2@q.io").passwordHash("h").build());
        User u3 = userRepository.saveAndFlush(
            User.builder().username("u3").email("u3@q.io").passwordHash("h").build());
        Challenge c = challengeRepository.saveAndFlush(buildChallenge("LDR01"));
        addParticipant(c, savedUser);
        addParticipant(c, u2);
        addParticipant(c, u3);
        participantRepository.addXp(c.getId(), u2.getId(),        90);
        participantRepository.addXp(c.getId(), savedUser.getId(), 45);
        participantRepository.addXp(c.getId(), u3.getId(),       120);
        List<ChallengeParticipant> board = participantRepository.findLeaderboard(c.getId());
        assertThat(board).hasSize(3);
        assertThat(board.get(0).getCurrentXp()).isEqualTo(120);
        assertThat(board.get(1).getCurrentXp()).isEqualTo(90);
        assertThat(board.get(2).getCurrentXp()).isEqualTo(45);
    }
    // ────────────────────────────── Helpers ──────────────────────────────
    private Task saveTask(String title, boolean completed, TaskCategory cat, TaskPriority pri) {
        return taskRepository.saveAndFlush(Task.builder().user(savedUser).title(title)
            .category(cat).priority(pri).xpValue(pri.getBaseXp()).completed(completed).build());
    }
    private Task saveCompletedTask(String title, TaskCategory cat, LocalDateTime completedAt) {
        return taskRepository.saveAndFlush(Task.builder().user(savedUser).title(title)
            .category(cat).priority(TaskPriority.MEDIUM).xpValue(10)
            .completed(true).completedAt(completedAt).build());
    }
    private Challenge buildChallenge(String code) {
        return Challenge.builder().name("Challenge " + code).description("desc")
            .inviteCode(code).startDate(LocalDateTime.now().minusHours(1))
            .endDate(LocalDateTime.now().plusDays(7)).targetXp(100).createdBy(savedUser).build();
    }
    private ChallengeParticipant addParticipant(Challenge c, User u) {
        return participantRepository.saveAndFlush(ChallengeParticipant.builder()
            .id(new ChallengeParticipantId(c.getId(), u.getId()))
            .challenge(c).user(u).currentXp(0).build());
    }
}
