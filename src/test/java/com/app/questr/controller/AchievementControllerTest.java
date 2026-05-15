package com.app.questr.controller;

import com.app.questr.dto.auth.AuthResponse;
import com.app.questr.dto.auth.SignupRequest;
import com.app.questr.dto.task.CreateTaskRequest;
import com.app.questr.dto.task.TaskResponse;
import com.app.questr.model.enums.TaskCategory;
import com.app.questr.model.enums.TaskPriority;
import com.app.questr.repository.TaskRepository;
import com.app.questr.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

/**
 * Module 5 — Achievement Controller Integration Tests.
 *
 * <p>Uses real Postgres via Testcontainers. Kafka and Redis are disabled
 * ({@code auto-startup=false} and {@code cache.type=none}) so no external
 * brokers are required. Badge logic is verified through the full
 * HTTP → Service → Repository stack.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.cache.type=none",
        "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AchievementControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureDs(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc       mockMvc;
    @Autowired TaskRepository taskRepo;

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
                                new SignupRequest("ach_hero", "ach@quest.io", "pass1234"))))
                .andReturn();
        accessToken = mapper.readValue(
                r.getResponse().getContentAsString(), AuthResponse.class).accessToken();
    }

    @AfterEach
    void cleanTasks() {
        taskRepo.deleteAll();
    }

    @AfterAll
    static void cleanUsers(@Autowired UserRepository userRepository) {
        userRepository.deleteAll();
    }

    // ── GET /api/v1/achievements ──────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("GET /achievements without token → 401")
    void achievements_noToken() throws Exception {
        mockMvc.perform(get("/api/v1/achievements"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    @DisplayName("GET /achievements returns correct structure for new user")
    void achievements_newUser() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/achievements")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earnedCount").isNumber())
                .andExpect(jsonPath("$.totalCount").isNumber())
                .andExpect(jsonPath("$.earned").isArray())
                .andExpect(jsonPath("$.locked").isArray())
                .andReturn();

        JsonNode root = om.readTree(r.getResponse().getContentAsString());
        int total  = root.get("totalCount").asInt();
        int earned = root.get("earnedCount").asInt();
        int locked = root.get("locked").size();

        // 18 badges seeded by V6 migration; new user has nothing yet
        assertThat(total).isEqualTo(18);
        assertThat(earned).isEqualTo(0);
        assertThat(locked).isEqualTo(18);
    }

    @Test
    @Order(3)
    @DisplayName("Completing first task awards FIRST_TASK badge")
    void completeTask_awardsFirstTaskBadge() throws Exception {
        UUID taskId = createAndCompleteTask(TaskPriority.MEDIUM);

        MvcResult r = mockMvc.perform(get("/api/v1/achievements")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = om.readTree(r.getResponse().getContentAsString());
        int earnedCount = root.get("earnedCount").asInt();

        // At minimum the FIRST_TASK badge should be earned
        assertThat(earnedCount).isGreaterThanOrEqualTo(1);

        // Verify FIRST_TASK is in the earned list
        boolean hasFirstTask = false;
        for (JsonNode badge : root.get("earned")) {
            if ("FIRST_TASK".equals(badge.get("name").asText())) {
                hasFirstTask = true;
                assertThat(badge.get("earned").asBoolean()).isTrue();
                assertThat(badge.get("earnedAt").asText()).isNotEmpty();
                break;
            }
        }
        assertThat(hasFirstTask).as("FIRST_TASK badge should be earned").isTrue();
    }

    @Test
    @Order(4)
    @DisplayName("Locked badges have progress hints")
    void lockedBadgesHaveProgressHints() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/achievements")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = om.readTree(r.getResponse().getContentAsString());
        for (JsonNode badge : root.get("locked")) {
            String name = badge.get("name").asText();
            // TIME-type badges (EARLY_BIRD) may have null hint; others must have one
            if (!"EARLY_BIRD".equals(name)) {
                assertThat(badge.get("progressHint").asText())
                        .as("Locked badge '%s' should have a progress hint", name)
                        .isNotEmpty();
            }
        }
    }

    // ── GET /api/v1/achievements/leaderboard ──────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("GET /leaderboard without token → 401")
    void leaderboard_noToken() throws Exception {
        mockMvc.perform(get("/api/v1/achievements/leaderboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    @DisplayName("GET /leaderboard returns array with rank and username")
    void leaderboard_returnsEntries() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/achievements/leaderboard")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode entries = om.readTree(r.getResponse().getContentAsString());
        assertThat(entries.isArray()).isTrue();
        assertThat(entries.size()).isGreaterThanOrEqualTo(1);

        JsonNode first = entries.get(0);
        assertThat(first.has("rank")).isTrue();
        assertThat(first.has("username")).isTrue();
        assertThat(first.has("level")).isTrue();
        assertThat(first.has("totalXp")).isTrue();
        assertThat(first.get("rank").asInt()).isEqualTo(1);
    }

    @Test
    @Order(7)
    @DisplayName("GET /leaderboard entries are sorted by XP descending")
    void leaderboard_sortedByXp() throws Exception {
        // Complete a few tasks to accumulate XP
        createAndCompleteTask(TaskPriority.HIGH);
        createAndCompleteTask(TaskPriority.HIGH);
        createAndCompleteTask(TaskPriority.HIGH);

        MvcResult r = mockMvc.perform(get("/api/v1/achievements/leaderboard")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode entries = om.readTree(r.getResponse().getContentAsString());
        for (int i = 0; i < entries.size() - 1; i++) {
            int xpA = entries.get(i).get("totalXp").asInt();
            int xpB = entries.get(i + 1).get("totalXp").asInt();
            assertThat(xpA).as("Leaderboard must be sorted descending by XP")
                    .isGreaterThanOrEqualTo(xpB);
        }
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private UUID createAndCompleteTask(TaskPriority priority) throws Exception {
        CreateTaskRequest req = new CreateTaskRequest(
                "Test task", null, TaskCategory.WORK, priority, null);
        MvcResult created = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID id = om.readValue(created.getResponse().getContentAsString(), TaskResponse.class).id();

        mockMvc.perform(patch("/api/v1/tasks/" + id + "/complete")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        return id;
    }
}

