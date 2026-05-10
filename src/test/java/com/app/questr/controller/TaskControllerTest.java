package com.app.questr.controller;

import com.app.questr.dto.auth.AuthResponse;
import com.app.questr.dto.auth.SignupRequest;
import com.app.questr.dto.task.CreateTaskRequest;
import com.app.questr.dto.task.TaskResponse;
import com.app.questr.dto.task.UpdateTaskRequest;
import com.app.questr.model.enums.TaskCategory;
import com.app.questr.model.enums.TaskPriority;
import com.app.questr.repository.TaskRepository;
import com.app.questr.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Module 4 — Task Controller Integration Tests.
 *
 * Uses a real PostgreSQL 15 container (Testcontainers) so Flyway migrations
 * run exactly as in production. Each test class lifecycle creates one user
 * and obtains tokens; inter-test cleanup deletes tasks only.
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
class TaskControllerTest {

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

    @Autowired MockMvc             mockMvc;
    @Autowired TaskRepository      taskRepo;

    private final ObjectMapper om = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // Auth tokens for primary user
    private static String accessToken;
    private static String accessToken2;   // second user (for 403 ownership checks)

    @BeforeAll
    static void registerUsers(@Autowired MockMvc mv) throws Exception {
        // Ensure both users exist (idempotent-ish via try/ignore)
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MvcResult r1 = mv.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SignupRequest("task_hero", "hero@task.io", "pass1234"))))
                .andReturn();
        accessToken = mapper.readValue(r1.getResponse().getContentAsString(), AuthResponse.class).accessToken();

        MvcResult r2 = mv.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SignupRequest("task_villain", "villain@task.io", "pass1234"))))
                .andReturn();
        accessToken2 = mapper.readValue(r2.getResponse().getContentAsString(), AuthResponse.class).accessToken();
    }

    @AfterEach
    void cleanupTasks() {
        taskRepo.deleteAll();
    }

    @AfterAll
    static void cleanupUsers(@Autowired UserRepository userRepository) {
        userRepository.deleteAll();
    }

    // ── POST /api/v1/tasks ────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /tasks → 201 with correct fields")
    void createTask_success() throws Exception {
        CreateTaskRequest req = new CreateTaskRequest(
                "Write unit tests", "Test everything", TaskCategory.DEV, TaskPriority.HIGH, null);

        MvcResult r = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Write unit tests"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.xpValue").value(20))
                .andExpect(jsonPath("$.completed").value(false))
                .andReturn();

        TaskResponse resp = om.readValue(r.getResponse().getContentAsString(), TaskResponse.class);
        org.assertj.core.api.Assertions.assertThat(resp.id()).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("POST /tasks blank title → 400")
    void createTask_blankTitle() throws Exception {
        CreateTaskRequest req = new CreateTaskRequest("", null, null, null, null);
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    @Order(3)
    @DisplayName("POST /tasks without token → 401")
    void createTask_noToken() throws Exception {
        CreateTaskRequest req = new CreateTaskRequest("No auth", null, null, null, null);
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/v1/tasks ─────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("GET /tasks returns paginated list for user")
    void listTasks() throws Exception {
        // Create a couple of tasks first
        createTaskViaApi("Task A", TaskPriority.LOW);
        createTaskViaApi("Task B", TaskPriority.MEDIUM);

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @Order(5)
    @DisplayName("GET /tasks?completed=false filters correctly")
    void listTasks_completedFilter() throws Exception {
        createTaskViaApi("Pending", TaskPriority.LOW);
        UUID id2 = createTaskViaApi("Done", TaskPriority.MEDIUM);
        completeTaskViaApi(id2);

        mockMvc.perform(get("/api/v1/tasks?completed=false")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Pending"));
    }

    @Test
    @Order(6)
    @DisplayName("GET /tasks?priority=HIGH filters correctly")
    void listTasks_priorityFilter() throws Exception {
        createTaskViaApi("Low task",  TaskPriority.LOW);
        createTaskViaApi("High task", TaskPriority.HIGH);

        mockMvc.perform(get("/api/v1/tasks?priority=HIGH")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].priority").value("HIGH"));
    }

    // ── GET /api/v1/tasks/{id} ────────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("GET /tasks/{id} returns single task")
    void getTask_success() throws Exception {
        UUID id = createTaskViaApi("Single task", TaskPriority.MEDIUM);

        mockMvc.perform(get("/api/v1/tasks/" + id)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("Single task"));
    }

    @Test
    @Order(8)
    @DisplayName("GET /tasks/{id} non-existent → 404")
    void getTask_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(9)
    @DisplayName("GET /tasks/{id} other user's task → 403")
    void getTask_forbidden() throws Exception {
        UUID id = createTaskViaApi("Hero's task", TaskPriority.HIGH);

        mockMvc.perform(get("/api/v1/tasks/" + id)
                        .header("Authorization", "Bearer " + accessToken2))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/v1/tasks/{id} ────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("PUT /tasks/{id} updates fields")
    void updateTask_success() throws Exception {
        UUID id = createTaskViaApi("Old title", TaskPriority.LOW);

        UpdateTaskRequest upd = new UpdateTaskRequest(
                "New title", "Updated desc", TaskCategory.WORK, TaskPriority.HIGH, null);

        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(upd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.xpValue").value(20));
    }

    @Test
    @Order(11)
    @DisplayName("PUT /tasks/{id} after completion → 400")
    void updateTask_alreadyCompleted() throws Exception {
        UUID id = createTaskViaApi("Must complete", TaskPriority.LOW);
        completeTaskViaApi(id);

        UpdateTaskRequest upd = new UpdateTaskRequest("Changed", null, null, null, null);
        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(upd)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(12)
    @DisplayName("PUT /tasks/{id} other user → 403")
    void updateTask_forbidden() throws Exception {
        UUID id = createTaskViaApi("Protected", TaskPriority.MEDIUM);

        UpdateTaskRequest upd = new UpdateTaskRequest("Hacked", null, null, null, null);
        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .header("Authorization", "Bearer " + accessToken2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(upd)))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /api/v1/tasks/{id} ─────────────────────────────────────────────

    @Test
    @Order(13)
    @DisplayName("DELETE /tasks/{id} → 204 and task gone")
    void deleteTask_success() throws Exception {
        UUID id = createTaskViaApi("To delete", TaskPriority.LOW);

        mockMvc.perform(delete("/api/v1/tasks/" + id)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // Confirm gone
        mockMvc.perform(get("/api/v1/tasks/" + id)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(14)
    @DisplayName("DELETE /tasks/{id} other user → 403")
    void deleteTask_forbidden() throws Exception {
        UUID id = createTaskViaApi("Protected delete", TaskPriority.HIGH);

        mockMvc.perform(delete("/api/v1/tasks/" + id)
                        .header("Authorization", "Bearer " + accessToken2))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /api/v1/tasks/{id}/complete ─────────────────────────────────────

    @Test
    @Order(15)
    @DisplayName("PATCH /tasks/{id}/complete → 200, completed=true, XP awarded")
    void completeTask_success() throws Exception {
        UUID id = createTaskViaApi("Quest done", TaskPriority.HIGH);

        mockMvc.perform(patch("/api/v1/tasks/" + id + "/complete")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.xpValue").value(20)); // HIGH=20, no deadline bonus, streak=0
    }

    @Test
    @Order(16)
    @DisplayName("PATCH /tasks/{id}/complete deadline bonus → xpValue = base + 5")
    void completeTask_deadlineBonus() throws Exception {
        LocalDateTime futureDeadline = LocalDateTime.now().plusDays(2);
        UUID id = createTaskViaApiWithDeadline("Deadline task", TaskPriority.MEDIUM, futureDeadline);

        mockMvc.perform(patch("/api/v1/tasks/" + id + "/complete")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                // MEDIUM=10 + 5 deadline bonus = 15
                .andExpect(jsonPath("$.xpValue").value(15));
    }

    @Test
    @Order(17)
    @DisplayName("PATCH /tasks/{id}/complete already done → 400")
    void completeTask_alreadyDone() throws Exception {
        UUID id = createTaskViaApi("Double complete", TaskPriority.LOW);
        completeTaskViaApi(id);

        mockMvc.perform(patch("/api/v1/tasks/" + id + "/complete")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(18)
    @DisplayName("PATCH /tasks/{id}/complete other user → 403")
    void completeTask_forbidden() throws Exception {
        UUID id = createTaskViaApi("Others task", TaskPriority.LOW);

        mockMvc.perform(patch("/api/v1/tasks/" + id + "/complete")
                        .header("Authorization", "Bearer " + accessToken2))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID createTaskViaApi(String title, TaskPriority priority) throws Exception {
        return createTaskViaApiWithDeadline(title, priority, null);
    }

    private UUID createTaskViaApiWithDeadline(String title, TaskPriority priority, LocalDateTime deadline)
            throws Exception {
        CreateTaskRequest req = new CreateTaskRequest(title, null, TaskCategory.WORK, priority, deadline);
        MvcResult r = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return om.readValue(r.getResponse().getContentAsString(), TaskResponse.class).id();
    }

    private void completeTaskViaApi(UUID taskId) throws Exception {
        mockMvc.perform(patch("/api/v1/tasks/" + taskId + "/complete")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }
}



















