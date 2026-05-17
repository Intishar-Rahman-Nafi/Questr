package com.app.questr.controller;
import com.app.questr.dto.auth.AuthResponse;
import com.app.questr.dto.auth.SignupRequest;
import com.app.questr.dto.task.CreateTaskRequest;
import com.app.questr.dto.task.TaskResponse;
import com.app.questr.model.enums.TaskCategory;
import com.app.questr.model.enums.TaskPriority;
import com.app.questr.repository.TaskRepository;
import com.app.questr.repository.UserBadgeRepository;
import com.app.questr.repository.UserRepository;
import com.app.questr.repository.UserStatsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.cache.type=none",
        "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DashboardControllerTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @DynamicPropertySource
    static void configureDs(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired MockMvc        mockMvc;
    @Autowired TaskRepository taskRepo;
    @Autowired UserRepository userRepo;
    @Autowired UserStatsRepository userStatsRepo;
    @Autowired UserBadgeRepository userBadgeRepo;
    @Autowired RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper om = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static String accessToken;
    @BeforeAll
    static void registerUser(@Autowired MockMvc mv) throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MvcResult r = mv.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new SignupRequest("dash_hero", "dash@quest.io", "pass1234"))))
                .andReturn();
        accessToken = mapper.readValue(
                r.getResponse().getContentAsString(), AuthResponse.class).accessToken();
    }
    @AfterEach
    void cleanTasks() {
        taskRepo.deleteAll();
        // Evict the Redis dashboard cache and reset UserStats so every test
        // starts from a clean baseline — prevents stale cache hits across tests.
        userRepo.findByUsername("dash_hero").ifPresent(u -> {
            // 1. Evict manual Redis cache written by DashboardService
            try { redisTemplate.delete("dashboard:" + u.getId()); } catch (Exception ignored) {}
            // 2. Remove any badges earned during the test
            userBadgeRepo.findByUserId(u.getId()).forEach(userBadgeRepo::delete);
            // 3. Reset UserStats to registration baseline
            userStatsRepo.findByUserId(u.getId()).ifPresent(s -> {
                s.setTotalXp(0);
                s.setLevel(1);
                s.setCurrentStreak(0);
                s.setLongestStreak(0);
                s.setTasksCompleted(0);
                s.setLastActivityDate(null);
                userStatsRepo.save(s);
            });
        });
    }
    @AfterAll
    static void cleanUsers(@Autowired UserRepository userRepository) {
        userRepository.deleteAll();
    }
    @Test @Order(1) @DisplayName("GET /dashboard without token -> 401")
    void dashboard_noToken() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
    }
    @Test @Order(2) @DisplayName("GET /dashboard returns correct structure for fresh user")
    void dashboard_freshUser_structure() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalXp").isNumber())
                .andExpect(jsonPath("$.level").isNumber())
                .andExpect(jsonPath("$.xpToNextLevel").isNumber())
                .andExpect(jsonPath("$.currentStreak").isNumber())
                .andExpect(jsonPath("$.longestStreak").isNumber())
                .andExpect(jsonPath("$.tasksCompleted").isNumber())
                .andExpect(jsonPath("$.completionRate").isNumber())
                .andExpect(jsonPath("$.weeklyCompletions").isArray())
                .andExpect(jsonPath("$.categoryBreakdown").isArray())
                .andReturn();
        JsonNode root = om.readTree(r.getResponse().getContentAsString());
        assertThat(root.get("totalXp").asInt()).isEqualTo(0);
        assertThat(root.get("level").asInt()).isEqualTo(1);
        assertThat(root.get("currentStreak").asInt()).isEqualTo(0);
        assertThat(root.get("tasksCompleted").asInt()).isEqualTo(0);
        assertThat(root.get("completionRate").asDouble()).isEqualTo(0.0);
        assertThat(root.get("weeklyCompletions").size()).isEqualTo(7);
        assertThat(root.get("categoryBreakdown").size()).isEqualTo(0);
    }
    @Test @Order(3) @DisplayName("xpToNextLevel is 380 for a fresh level-1 user")
    void dashboard_xpToNextLevel_level1() throws Exception {
        JsonNode root = om.readTree(mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(root.get("xpToNextLevel").asInt()).isEqualTo(380);
    }
    @Test @Order(4) @DisplayName("weeklyCompletions starts MONDAY and ends SUNDAY")
    void dashboard_weeklyCompletions_hasDayNames() throws Exception {
        JsonNode weekly = om.readTree(mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("weeklyCompletions");
        assertThat(weekly.get(0).get("dayOfWeek").asText()).isEqualTo("MONDAY");
        assertThat(weekly.get(6).get("dayOfWeek").asText()).isEqualTo("SUNDAY");
        for (JsonNode e : weekly) {
            assertThat(e.has("date")).isTrue();
            assertThat(e.has("count")).isTrue();
        }
    }
    @Test @Order(5) @DisplayName("Dashboard reflects stats after completing tasks")
    void dashboard_afterTasks_statsUpdated() throws Exception {
        createAndCompleteTask(TaskPriority.HIGH, TaskCategory.WORK);
        createAndCompleteTask(TaskPriority.MEDIUM, TaskCategory.HEALTH);
        JsonNode root = om.readTree(mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(root.get("totalXp").asInt()).isGreaterThan(0);
        assertThat(root.get("tasksCompleted").asInt()).isEqualTo(2);
        assertThat(root.get("completionRate").asDouble()).isEqualTo(100.0);
        long todayCount = 0;
        for (JsonNode e : root.get("weeklyCompletions")) todayCount += e.get("count").asLong();
        assertThat(todayCount).isGreaterThanOrEqualTo(2);
        JsonNode bd = root.get("categoryBreakdown");
        assertThat(bd.size()).isGreaterThanOrEqualTo(1);
        for (JsonNode cat : bd) {
            assertThat(cat.get("percentage").asDouble()).isGreaterThan(0.0);
            assertThat(cat.get("percentage").asDouble()).isLessThanOrEqualTo(100.0);
        }
    }
    @Test @Order(6) @DisplayName("completionRate is 50.0 when 1 of 2 tasks completed")
    void dashboard_completionRate_withIncompleteTask() throws Exception {
        createTask(TaskPriority.LOW, TaskCategory.PERSONAL);
        completeTask(createTask(TaskPriority.MEDIUM, TaskCategory.WORK));
        JsonNode root = om.readTree(mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(root.get("completionRate").asDouble()).isEqualTo(50.0);
    }
    @Test @Order(7) @DisplayName("xpToNextLevel decreases after earning XP")
    void dashboard_xpToNextLevel_decreasesAfterXP() throws Exception {
        int before = om.readTree(mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("xpToNextLevel").asInt();
        createAndCompleteTask(TaskPriority.HIGH, TaskCategory.WORK);
        int after = om.readTree(mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("xpToNextLevel").asInt();
        assertThat(after).isLessThan(before);
    }
    @Test @Order(8) @DisplayName("GET /dashboard/history without token -> 401")
    void history_noToken() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/history")).andExpect(status().isUnauthorized());
    }
    @Test @Order(9) @DisplayName("history returns 4 weeks by default")
    void history_defaultFourWeeks() throws Exception {
        JsonNode entries = om.readTree(mockMvc.perform(get("/api/v1/dashboard/history")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(entries.isArray()).isTrue();
        assertThat(entries.size()).isEqualTo(4);
    }
    @Test @Order(10) @DisplayName("history?weeks=2 returns exactly 2 entries")
    void history_customWeeks() throws Exception {
        JsonNode entries = om.readTree(mockMvc.perform(get("/api/v1/dashboard/history?weeks=2")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(entries.size()).isEqualTo(2);
    }
    @Test @Order(11) @DisplayName("history is capped at 12 weeks max")
    void history_cappedAt12() throws Exception {
        JsonNode entries = om.readTree(mockMvc.perform(get("/api/v1/dashboard/history?weeks=99")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(entries.size()).isLessThanOrEqualTo(12);
    }
    @Test @Order(12) @DisplayName("history entries have weekStart, weekEnd, tasksCompleted")
    void history_entryShape() throws Exception {
        JsonNode entries = om.readTree(mockMvc.perform(get("/api/v1/dashboard/history?weeks=1")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(entries.size()).isEqualTo(1);
        JsonNode e = entries.get(0);
        assertThat(e.has("weekStart")).isTrue();
        assertThat(e.has("weekEnd")).isTrue();
        assertThat(e.has("tasksCompleted")).isTrue();
        assertThat(e.get("tasksCompleted").asInt()).isGreaterThanOrEqualTo(0);
    }
    @Test @Order(13) @DisplayName("history entries are sorted oldest to newest")
    void history_sortedOldestToNewest() throws Exception {
        JsonNode entries = om.readTree(mockMvc.perform(get("/api/v1/dashboard/history?weeks=4")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (int i = 0; i < entries.size() - 1; i++) {
            assertThat(entries.get(i).get("weekStart").asText()
                    .compareTo(entries.get(i + 1).get("weekStart").asText())).isLessThan(0);
        }
    }
    @Test @Order(14) @DisplayName("history current week reflects completed tasks")
    void history_currentWeekReflectsCompletions() throws Exception {
        createAndCompleteTask(TaskPriority.HIGH, TaskCategory.DEV);
        createAndCompleteTask(TaskPriority.HIGH, TaskCategory.DEV);
        JsonNode entries = om.readTree(mockMvc.perform(get("/api/v1/dashboard/history?weeks=1")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(entries.get(0).get("tasksCompleted").asInt()).isGreaterThanOrEqualTo(2);
    }
    // helpers
    private UUID createTask(TaskPriority p, TaskCategory c) throws Exception {
        MvcResult cr = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(new CreateTaskRequest("Test", null, c, p, null))))
                .andExpect(status().isCreated()).andReturn();
        return om.readValue(cr.getResponse().getContentAsString(), TaskResponse.class).id();
    }
    private void completeTask(UUID id) throws Exception {
        mockMvc.perform(patch("/api/v1/tasks/" + id + "/complete")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }
    private void createAndCompleteTask(TaskPriority p, TaskCategory c) throws Exception {
        completeTask(createTask(p, c));
    }
}
