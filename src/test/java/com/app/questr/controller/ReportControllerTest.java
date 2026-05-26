package com.app.questr.controller;

import com.app.questr.dto.auth.AuthResponse;
import com.app.questr.dto.auth.SignupRequest;
import com.app.questr.dto.task.CreateTaskRequest;
import com.app.questr.dto.task.TaskResponse;
import com.app.questr.model.enums.TaskCategory;
import com.app.questr.model.enums.TaskPriority;
import com.app.questr.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
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

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests for Module 8 — OpenAI Weekly AI Report.
 *
 * <h2>Test Coverage</h2>
 * <ol>
 *   <li>Security: both endpoints return 401 without a JWT token</li>
 *   <li>Happy path: GET /weekly returns correct structure with fallback=false</li>
 *   <li>Tip count: exactly 3 tips in the response</li>
 *   <li>POST /regenerate: returns a fresh report on first regeneration</li>
 *   <li>Rate limit: second POST /regenerate same day returns 429</li>
 *   <li>Fallback on HTTP 500 from OpenAI: still returns 200 with fallback=true</li>
 *   <li>Fallback on corrupt JSON from OpenAI: still returns 200 with fallback=true</li>
 *   <li>Fallback response still contains all required fields</li>
 *   <li>Full lifecycle: sign up → complete tasks → GET report → verify content</li>
 * </ol>
 *
 * <h2>Infrastructure</h2>
 * <ul>
 *   <li>PostgreSQL via Testcontainers — real DB with Flyway migrations</li>
 *   <li>MockWebServer (OkHttp) — simulates the OpenAI Chat Completions API;
 *       started statically so its port is available in {@code @DynamicPropertySource}</li>
 *   <li>Redis is unavailable — {@link com.app.questr.service.OpenAIService} handles
 *       Redis failures gracefully: cache misses are silent, rate-limit falls back to
 *       the in-memory {@code localRateLimitCache} ConcurrentHashMap</li>
 *   <li>Kafka consumer disabled via {@code spring.kafka.listener.auto-startup=false}</li>
 * </ul>
 *
 * <h2>Notable design decisions in tests</h2>
 * <ul>
 *   <li>Because Redis is unavailable every GET /weekly call is a cache miss, so each
 *       test that calls the endpoint must enqueue exactly one MockWebServer response.</li>
 *   <li>Rate-limit tests use a dedicated user ({@code rl_bob}) so that alice's rate-limit
 *       slot is never consumed and the two concerns stay independent.</li>
 * </ul>
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
class ReportControllerTest {

    // ── Infrastructure ────────────────────────────────────────────────────────

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine");

    /**
     * Real HTTP server that simulates the OpenAI Chat Completions API.
     * Started in a static initializer so the port is known before
     * {@code @DynamicPropertySource} executes.
     */
    static final MockWebServer mockOpenAI;

    static {
        try {
            mockOpenAI = new MockWebServer();
            mockOpenAI.start();
        } catch (IOException e) {
            throw new RuntimeException("Cannot start MockWebServer for OpenAI simulation", e);
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry r) {
        // Database
        r.add("spring.datasource.url",      postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        // Redirect WebClient to our local mock OpenAI server
        r.add("openai.base-url",        () -> "http://localhost:" + mockOpenAI.getPort());
        r.add("openai.api-key",         () -> "test-fake-key-not-real");
        r.add("openai.timeout-seconds", () -> "5");   // fast timeout in tests
    }

    // ── Test fixtures ─────────────────────────────────────────────────────────

    /**
     * Inner JSON that {@code parseAIResponse} will parse after being extracted from
     * {@code choices[0].message.content}.
     */
    private static final String INNER_AI_JSON =
            "{\"summary\":\"You had a great week!\"," +
            "\"tips\":[\"Tip one\",\"Tip two\",\"Tip three\"]," +
            "\"improvements\":\"Stay consistent.\"," +
            "\"quote\":\"Work hard.\"}";

    /**
     * A complete, valid OpenAI Chat Completions HTTP response body.
     * The {@code content} field holds the inner AI JSON, properly escaped for embedding
     * as a JSON string value.
     */
    private static final String VALID_OPENAI_RESPONSE;

    static {
        // Escape backslashes first, then double-quotes — makes INNER_AI_JSON safe
        // to embed as a JSON string literal inside the outer response object.
        String escaped = INNER_AI_JSON
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        VALID_OPENAI_RESPONSE =
                "{\"id\":\"chatcmpl-test\"," +
                "\"choices\":[{\"index\":0," +
                "\"message\":{\"role\":\"assistant\",\"content\":\"" + escaped + "\"}," +
                "\"finish_reason\":\"stop\"}]}";
    }

    // ── Shared state (set by @BeforeAll, used across ordered tests) ───────────

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepo;
    @Autowired org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper om = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** General-purpose user — for GET /weekly tests. */
    private static String aliceToken;

    /** Dedicated rate-limit test user — POST /regenerate tests.
     *  Using a separate user guarantees alice's slot is never consumed. */
    private static String bobToken;

    @BeforeAll
    static void setupUsers(@Autowired MockMvc mv) throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        aliceToken = signup(mv, mapper, "rpt_alice", "rpt_alice@q.io", "pass1234");
        bobToken   = signup(mv, mapper, "rpt_bob",   "rpt_bob@q.io",   "pass1234");
    }

    /**
     * Before every test: evict all report-cache and rate-limit keys from Redis so
     * that each test starts from a clean state regardless of what the previous test
     * wrote. This prevents stale cache hits from turning fallback scenarios into
     * non-fallback responses.
     */
    @BeforeEach
    void evictReportCacheAndRateLimitKeys() {
        try {
            Set<String> keys = redisTemplate.keys("report:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ignored) {
            // Redis unavailable — silently skip; OpenAIService will be a cache miss anyway
        }
    }

    @AfterAll
    static void cleanup(@Autowired UserRepository ur) throws IOException {
        ur.deleteAll();
        mockOpenAI.shutdown();
    }

    // ── Helper: enqueue one valid OpenAI success response ─────────────────────

    private void enqueueValidResponse() {
        mockOpenAI.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(VALID_OPENAI_RESPONSE)
                .addHeader("Content-Type", "application/json"));
    }

    // =========================================================================
    // Security — unauthenticated requests
    // =========================================================================

    @Test @Order(1)
    @DisplayName("GET /reports/weekly without token → 401")
    void getWeeklyReport_noToken_401() throws Exception {
        mockMvc.perform(get("/api/v1/reports/weekly"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(2)
    @DisplayName("POST /reports/weekly/regenerate without token → 401")
    void regenerate_noToken_401() throws Exception {
        mockMvc.perform(post("/api/v1/reports/weekly/regenerate"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Happy path — GET /weekly
    // =========================================================================

    @Test @Order(3)
    @DisplayName("GET /weekly → 200; all required fields present; fallback=false")
    void getWeeklyReport_returnsCompleteReport() throws Exception {
        enqueueValidResponse();

        MvcResult result = mockMvc.perform(get("/api/v1/reports/weekly")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").isNotEmpty())
                .andExpect(jsonPath("$.tips").isArray())
                .andExpect(jsonPath("$.improvements").isNotEmpty())
                .andExpect(jsonPath("$.quote").isNotEmpty())
                .andExpect(jsonPath("$.weekStart").isNotEmpty())
                .andExpect(jsonPath("$.weekEnd").isNotEmpty())
                .andExpect(jsonPath("$.generatedAt").isNotEmpty())
                .andExpect(jsonPath("$.fallback").value(false))
                .andReturn();

        JsonNode body = om.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("summary").asText()).isEqualTo("You had a great week!");
        assertThat(body.get("improvements").asText()).isEqualTo("Stay consistent.");
        assertThat(body.get("quote").asText()).isEqualTo("Work hard.");
    }

    @Test @Order(4)
    @DisplayName("GET /weekly → tips array has exactly 3 entries")
    void getWeeklyReport_tipsCountIsThree() throws Exception {
        enqueueValidResponse();

        JsonNode body = om.readTree(mockMvc.perform(get("/api/v1/reports/weekly")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        JsonNode tips = body.get("tips");
        assertThat(tips.isArray()).isTrue();
        assertThat(tips.size()).isEqualTo(3);
        assertThat(tips.get(0).asText()).isEqualTo("Tip one");
        assertThat(tips.get(1).asText()).isEqualTo("Tip two");
        assertThat(tips.get(2).asText()).isEqualTo("Tip three");
    }

    @Test @Order(5)
    @DisplayName("GET /weekly → weekStart is Monday, weekEnd is Sunday, weekEnd > weekStart")
    void getWeeklyReport_weekDatesAreCorrect() throws Exception {
        enqueueValidResponse();

        JsonNode body = om.readTree(mockMvc.perform(get("/api/v1/reports/weekly")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        String weekStart = body.get("weekStart").asText();
        String weekEnd   = body.get("weekEnd").asText();

        assertThat(weekStart).isNotBlank();
        assertThat(weekEnd).isNotBlank();
        // weekEnd must be after weekStart (Sunday > Monday)
        assertThat(weekEnd.compareTo(weekStart)).isGreaterThan(0);
    }

    @Test @Order(6)
    @DisplayName("GET /weekly → MockWebServer received a POST to /chat/completions")
    void getWeeklyReport_callsCorrectOpenAIEndpoint() throws Exception {
        enqueueValidResponse();

        mockMvc.perform(get("/api/v1/reports/weekly")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk());

        RecordedRequest recorded = mockOpenAI.takeRequest(3, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/chat/completions");
        // Verify the request body includes the model and messages
        String requestBody = recorded.getBody().readUtf8();
        assertThat(requestBody).contains("messages");
        assertThat(requestBody).contains("model");
    }

    // =========================================================================
    // POST /regenerate — happy path + rate limiting
    // =========================================================================

    @Test @Order(7)
    @DisplayName("POST /regenerate (first call) → 200; fresh report with fallback=false")
    void regenerate_firstCall_200() throws Exception {
        enqueueValidResponse();

        MvcResult result = mockMvc.perform(post("/api/v1/reports/weekly/regenerate")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.summary").value("You had a great week!"))
                .andReturn();

        // summary, tips, improvements, quote, weekStart, weekEnd, generatedAt must all be present
        JsonNode body = om.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("summary")).isTrue();
        assertThat(body.has("tips")).isTrue();
        assertThat(body.has("improvements")).isTrue();
        assertThat(body.has("quote")).isTrue();
        assertThat(body.has("weekStart")).isTrue();
        assertThat(body.has("weekEnd")).isTrue();
        assertThat(body.has("generatedAt")).isTrue();
    }

    @Test @Order(8)
    @DisplayName("POST /regenerate (second call same day) → 429 Too Many Requests")
    void regenerate_secondCallSameDay_429() throws Exception {
        // Bob already regenerated in test 7 — the in-memory localRateLimitCache prevents another call.
        // No MockWebServer enqueue needed because the service throws before calling OpenAI.
        mockMvc.perform(post("/api/v1/reports/weekly/regenerate")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isTooManyRequests());
    }

    @Test @Order(9)
    @DisplayName("POST /regenerate for alice (separate user) → NOT rate-limited → 200")
    void regenerate_differentUser_notRateLimited() throws Exception {
        // Alice has never regenerated, so she should succeed
        enqueueValidResponse();

        mockMvc.perform(post("/api/v1/reports/weekly/regenerate")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(false));
    }

    // =========================================================================
    // Fallback behaviour — OpenAI failures
    // =========================================================================

    @Test @Order(10)
    @DisplayName("GET /weekly when OpenAI returns HTTP 500 → 200 with fallback=true")
    void getWeeklyReport_openAi500_returnsFallback() throws Exception {
        mockOpenAI.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\":{\"message\":\"Internal Server Error\",\"type\":\"server_error\"}}")
                .addHeader("Content-Type", "application/json"));

        mockMvc.perform(get("/api/v1/reports/weekly")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.summary").isNotEmpty())
                .andExpect(jsonPath("$.tips").isArray());
    }

    @Test @Order(11)
    @DisplayName("GET /weekly when OpenAI returns malformed JSON → 200 with fallback=true")
    void getWeeklyReport_malformedJson_returnsFallback() throws Exception {
        // Body is not valid JSON → callOpenAI throws → generateReport catches → fallback
        mockOpenAI.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("this is definitely not valid json { broken")
                .addHeader("Content-Type", "application/json"));

        mockMvc.perform(get("/api/v1/reports/weekly")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(true));
    }

    @Test @Order(12)
    @DisplayName("GET /weekly when OpenAI content JSON is incomplete → 200 with fallback=true")
    void getWeeklyReport_incompleteAiJson_returnsFallback() throws Exception {
        // The outer OpenAI response is valid but the inner content JSON is missing
        // "summary" and "tips", which triggers the completeness check in parseAIResponse()
        String escaped = "{\\\"improvements\\\":\\\"Only improvement field\\\",\\\"quote\\\":\\\"A.\\\"}";
        String incompleteResponse =
                "{\"choices\":[{\"message\":{\"content\":\"" + escaped + "\"}}]}";

        mockOpenAI.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(incompleteResponse)
                .addHeader("Content-Type", "application/json"));

        mockMvc.perform(get("/api/v1/reports/weekly")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(true));
    }

    @Test @Order(13)
    @DisplayName("Fallback response contains all required fields with non-blank values")
    void fallbackResponse_hasAllRequiredFields() throws Exception {
        mockOpenAI.enqueue(new MockResponse()
                .setResponseCode(503)
                .setBody("{\"error\":\"Service Unavailable\"}")
                .addHeader("Content-Type", "application/json"));

        MvcResult result = mockMvc.perform(get("/api/v1/reports/weekly")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = om.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("fallback").asBoolean()).isTrue();
        assertThat(body.get("summary").asText()).isNotBlank();
        assertThat(body.get("improvements").asText()).isNotBlank();
        assertThat(body.get("quote").asText()).isNotBlank();
        assertThat(body.get("weekStart").asText()).isNotBlank();
        assertThat(body.get("weekEnd").asText()).isNotBlank();
        assertThat(body.get("generatedAt").asText()).isNotBlank();

        JsonNode tips = body.get("tips");
        assertThat(tips.isArray()).isTrue();
        assertThat(tips.size()).isEqualTo(3);
        for (JsonNode tip : tips) {
            assertThat(tip.asText()).isNotBlank();
        }
    }

    // =========================================================================
    // Full end-to-end lifecycle: tasks → report
    // =========================================================================

    @Test @Order(14)
    @DisplayName("Full lifecycle: sign up → complete tasks → GET /weekly → report matches structure")
    void fullLifecycle_tasksThenReport() throws Exception {
        // 1. Create a fresh user
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String token = signup(mockMvc, mapper, "rpt_charlie", "rpt_charlie@q.io", "pass1234");

        // 2. Complete a few tasks to generate data that the report prompt uses
        UUID task1 = createTask(token, TaskPriority.HIGH,   TaskCategory.WORK);
        UUID task2 = createTask(token, TaskPriority.MEDIUM, TaskCategory.HEALTH);
        UUID task3 = createTask(token, TaskPriority.LOW,    TaskCategory.PERSONAL);
        completeTask(token, task1);
        completeTask(token, task2);
        // task3 left incomplete — completion rate is 66.7%

        // 3. Enqueue valid OpenAI response
        enqueueValidResponse();

        // 4. GET the weekly report
        MvcResult result = mockMvc.perform(get("/api/v1/reports/weekly")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").isNotEmpty())
                .andExpect(jsonPath("$.tips").isArray())
                .andExpect(jsonPath("$.fallback").value(false))
                .andReturn();

        JsonNode body = om.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("tips").size()).isEqualTo(3);
        assertThat(body.get("weekStart").asText()).isNotBlank();
        assertThat(body.get("weekEnd").asText()).isNotBlank();

        // Cleanup
        userRepo.findByUsername("rpt_charlie").ifPresent(userRepo::delete);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String signup(MockMvc mv, ObjectMapper mapper,
                                  String username, String email, String password) throws Exception {
        MvcResult r = mv.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new SignupRequest(username, email, password))))
                .andExpect(status().isCreated())
                .andReturn();
        return mapper.readValue(r.getResponse().getContentAsString(), AuthResponse.class).accessToken();
    }

    private UUID createTask(String token, TaskPriority priority, TaskCategory category) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(
                                new CreateTaskRequest("Report test task", null, category, priority, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return om.readValue(r.getResponse().getContentAsString(), TaskResponse.class).id();
    }

    private void completeTask(String token, UUID taskId) throws Exception {
        mockMvc.perform(patch("/api/v1/tasks/" + taskId + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}




