package com.app.questr.service;

import com.app.questr.dto.report.AIReportResponse;
import com.app.questr.exception.ApiException;
import com.app.questr.model.entity.Task;
import com.app.questr.model.entity.UserStats;
import com.app.questr.repository.TaskRepository;
import com.app.questr.repository.UserStatsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Module 8 — OpenAI Weekly AI Report service.
 *
 * <h2>Pipeline</h2>
 * <ol>
 *   <li>{@link #getWeeklyReport(UUID)} — Redis cache first (24 h TTL per user-week key);
 *       on miss: gather data, build prompt, call gpt-4o-mini, parse JSON.</li>
 *   <li>{@link #regenerateWeeklyReport(UUID)} — Bypass cache; check distributed rate
 *       limit (1 per day per user) backed by Redis + in-memory fallback; then same
 *       generate pipeline as above.</li>
 *   <li>On ANY call failure (network, auth, parse) {@link #buildFallbackReport} is
 *       returned — the report is still cached so retries are cheap.</li>
 * </ol>
 *
 * <h2>Cache keys</h2>
 * <ul>
 *   <li>Report:    {@code report:{userId}:{isoYear}-W{isoWeek}}</li>
 *   <li>Rate limit:{@code report:regen:{userId}:{date}}</li>
 * </ul>
 *
 * <h2>Rate limiting</h2>
 * Redis is the primary distributed store. An in-memory {@link ConcurrentHashMap}
 * acts as a JVM-local fallback, making the rate limit deterministic even when Redis
 * is unavailable (important for test reliability).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class OpenAIService {

    // ── Cache / rate-limit key prefixes ───────────────────────────────────────
    private static final String REPORT_CACHE_PREFIX  = "report:";
    private static final String REGEN_RL_PREFIX      = "report:regen:";
    private static final Duration REPORT_TTL         = Duration.ofHours(24);
    private static final Duration REGEN_RL_TTL       = Duration.ofHours(24);

    // ── Static fallback content ───────────────────────────────────────────────
    private static final String FALLBACK_SUMMARY =
            "You had a productive week! Keep building those positive habits and " +
            "aim for consistency rather than perfection.";
    private static final List<String> FALLBACK_TIPS = List.of(
            "Break large tasks into focused 25-minute Pomodoro sessions.",
            "Use the 2-minute rule: if it takes under 2 minutes, do it now.",
            "Review your task list every morning to set your top 3 priorities."
    );
    private static final String FALLBACK_IMPROVEMENTS =
            "Try to maintain a consistent daily completion streak — even one " +
            "small task per day keeps momentum alive.";
    private static final String FALLBACK_QUOTE =
            "\"The secret of getting ahead is getting started.\" — Mark Twain";

    // ── In-memory rate-limit fallback (JVM-local when Redis is unavailable) ───
    private final Map<String, LocalDateTime> localRateLimitCache = new ConcurrentHashMap<>();

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final TaskRepository                taskRepository;
    private final UserStatsRepository           userStatsRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper                  objectMapper;

    @Qualifier("openAiWebClient")
    private final WebClient openAiWebClient;

    // ── Config values ─────────────────────────────────────────────────────────
    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    @Value("${openai.max-tokens:1000}")
    private int maxTokens;

    @Value("${openai.temperature:0.7}")
    private double temperature;

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Return the cached weekly report, generating a fresh one if not yet cached.
     * Cache TTL = 24 hours keyed by user + ISO week.
     */
    public AIReportResponse getWeeklyReport(UUID userId) {
        String cacheKey = buildCacheKey(userId);
        AIReportResponse cached = readFromCache(cacheKey);
        if (cached != null) {
            log.debug("Report cache hit for user {} week {}", userId, isoWeekTag());
            return cached;
        }
        AIReportResponse report = generateReport(userId);
        writeToCache(cacheKey, report, REPORT_TTL);
        return report;
    }

    /**
     * Force-regenerate the report, bypassing the 24 h cache.
     * Rate-limited: at most one forced regeneration per user per day.
     *
     * @throws ApiException (429) if the user has already regenerated today.
     */
    public AIReportResponse regenerateWeeklyReport(UUID userId) {
        String rlKey = buildRateLimitKey(userId);

        // Check rate limit (Redis + in-memory fallback)
        if (isRateLimited(rlKey)) {
            log.warn("Regeneration rate limit hit for user {}", userId);
            throw new ApiException(
                    "You have already regenerated your weekly report today. " +
                    "Try again tomorrow.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }

        // Generate fresh report
        AIReportResponse report = generateReport(userId);

        // Overwrite cache
        String cacheKey = buildCacheKey(userId);
        writeToCache(cacheKey, report, REPORT_TTL);

        // Record rate-limit token (Redis + in-memory)
        setRateLimitToken(rlKey);

        return report;
    }

    // =========================================================================
    // Core generation pipeline
    // =========================================================================

    private AIReportResponse generateReport(UUID userId) {
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate weekEnd   = weekStart.plusDays(6);

        try {
            UserStats stats = userStatsRepository.findByUserId(userId)
                    .orElseThrow(() -> new IllegalStateException("UserStats not found for " + userId));

            List<Task> weeklyTasks = taskRepository.findTasksForWeek(
                    userId, weekStart.atStartOfDay());

            String prompt = buildUserPrompt(weeklyTasks, stats);
            String rawJson = callOpenAI(buildSystemPrompt(), prompt);
            return parseAIResponse(rawJson, weekStart, weekEnd);

        } catch (Exception e) {
            log.warn("OpenAI report generation failed for user {} — using fallback. Reason: {}",
                    userId, e.getMessage());
            return buildFallbackReport(weekStart, weekEnd);
        }
    }

    // ── Prompt construction ───────────────────────────────────────────────────

    /**
     * System prompt that instructs GPT to act as a JSON-only productivity coach.
     */
    private String buildSystemPrompt() {
        return """
                You are an expert productivity coach. You MUST respond with ONLY a valid JSON object.
                Do NOT include any markdown, code fences, or explanations outside the JSON.
                The JSON must have exactly these keys:
                  "summary"      — string: 2-3 sentences of personalised weekly feedback
                  "tips"         — array of exactly 3 short, actionable productivity tips
                  "improvements" — string: 1-2 sentences on the main area to improve
                  "quote"        — string: one motivational quote with attribution
                """;
    }

    /**
     * User prompt containing the week's actual data.
     * Deliberately includes enough context for GPT to generate personalised advice.
     */
    private String buildUserPrompt(List<Task> tasks, UserStats stats) {
        long completed  = tasks.stream().filter(t -> Boolean.TRUE.equals(t.getCompleted())).count();
        long total      = tasks.size();
        double rate     = total > 0 ? Math.round((double) completed / total * 1000.0) / 10.0 : 0.0;

        // Category breakdown for this week
        String categoryBreakdown = tasks.stream()
                .filter(t -> Boolean.TRUE.equals(t.getCompleted()) && t.getCategory() != null)
                .collect(Collectors.groupingBy(t -> t.getCategory().name(), Collectors.counting()))
                .entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining(", "));

        // XP earned this week (approximation using current total — context for GPT)
        return """
                Here is my productivity data for this week:

                Tasks completed : %d out of %d (%.1f%% completion rate)
                Total XP earned : %d
                Current level   : %d
                Current streak  : %d days
                Longest streak  : %d days
                Category breakdown (completed tasks): %s

                Based on this data, provide your JSON response with personalised
                summary, 3 tips, improvements, and a motivational quote.
                """.formatted(
                completed, total, rate,
                stats.getTotalXp(),
                stats.getLevel(),
                stats.getCurrentStreak(),
                stats.getLongestStreak(),
                categoryBreakdown.isBlank() ? "none this week" : categoryBreakdown
        );
    }

    // ── OpenAI API call ───────────────────────────────────────────────────────

    /**
     * Call the OpenAI Chat Completions API and return the raw message content.
     * Any error (network, HTTP 4xx/5xx, empty response) propagates as an exception
     * so the caller can activate the fallback.
     */
    private String callOpenAI(String systemPrompt, String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model",           model,
                "temperature",     temperature,
                "max_tokens",      maxTokens,
                "response_format", Map.of("type", "json_object"),
                "messages",        List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user",   "content", userPrompt)
                )
        );

        String rawResponse = openAiWebClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), resp ->
                        resp.bodyToMono(String.class).flatMap(body -> {
                            log.warn("OpenAI error HTTP {}: {}", resp.statusCode(), body);
                            return Mono.error(new RuntimeException("OpenAI returned " + resp.statusCode()));
                        })
                )
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(35));   // Hard timeout slightly above request timeout

        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IllegalStateException("Empty response from OpenAI");
        }

        // Extract content from choices[0].message.content
        try {
            JsonNode root    = objectMapper.readTree(rawResponse);
            JsonNode choices = root.path("choices");
            if (choices.isEmpty()) {
                throw new IllegalStateException("OpenAI response has no choices");
            }
            return choices.get(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to extract OpenAI message content: " + e.getMessage(), e);
        }
    }

    // ── Response parsing ──────────────────────────────────────────────────────

    private AIReportResponse parseAIResponse(String content, LocalDate weekStart, LocalDate weekEnd) {
        try {
            JsonNode root = objectMapper.readTree(content);

            // Check presence/completeness BEFORE applying default fallback values.
            // root.path("x").isMissingNode() is true when the key is absent from the JSON.
            // root.path("x").asText() returns "" for a MissingNode — so isBlank() catches it.
            JsonNode summaryNode = root.path("summary");
            JsonNode tipsNode    = root.path("tips");

            if (summaryNode.isMissingNode() || summaryNode.asText().isBlank()
                    || tipsNode.isMissingNode() || !tipsNode.isArray() || tipsNode.isEmpty()) {
                log.warn("OpenAI responded but content is incomplete (missing summary/tips) — using fallback");
                return buildFallbackReport(weekStart, weekEnd);
            }

            String       summary      = summaryNode.asText();
            List<String> tips         = parseTips(tipsNode);
            String       improvements = root.path("improvements").asText(FALLBACK_IMPROVEMENTS);
            String       quote        = root.path("quote").asText(FALLBACK_QUOTE);

            return new AIReportResponse(summary, tips, improvements, quote,
                    weekStart, weekEnd, LocalDateTime.now(), false);

        } catch (Exception e) {
            log.warn("Failed to parse OpenAI JSON response: {}", e.getMessage());
            return buildFallbackReport(weekStart, weekEnd);
        }
    }

    private List<String> parseTips(JsonNode tipsNode) {
        if (tipsNode == null || !tipsNode.isArray() || tipsNode.isEmpty()) {
            return FALLBACK_TIPS;
        }
        List<String> tips = new java.util.ArrayList<>();
        for (JsonNode t : tipsNode) {
            String text = t.isTextual() ? t.asText() : t.toString();
            if (!text.isBlank()) tips.add(text);
        }
        return tips.isEmpty() ? FALLBACK_TIPS : tips;
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    /**
     * Static fallback report — always safe, always returns a complete response.
     * Returned when the OpenAI call fails for any reason.
     */
    public AIReportResponse buildFallbackReport(LocalDate weekStart, LocalDate weekEnd) {
        return new AIReportResponse(
                FALLBACK_SUMMARY,
                FALLBACK_TIPS,
                FALLBACK_IMPROVEMENTS,
                FALLBACK_QUOTE,
                weekStart,
                weekEnd,
                LocalDateTime.now(),
                true    // fallback = true
        );
    }

    // =========================================================================
    // Rate limiting (Redis primary + in-memory fallback)
    // =========================================================================

    private boolean isRateLimited(String rlKey) {
        // Redis check
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(rlKey))) return true;
        } catch (Exception e) {
            log.warn("Redis rate-limit check failed, falling back to in-memory: {}", e.getMessage());
        }

        // In-memory fallback
        LocalDateTime lastRegen = localRateLimitCache.get(rlKey);
        return lastRegen != null && LocalDateTime.now().isBefore(lastRegen.plus(REGEN_RL_TTL));
    }

    private void setRateLimitToken(String rlKey) {
        // Redis (distributed)
        try {
            redisTemplate.opsForValue().set(rlKey, "1", REGEN_RL_TTL);
        } catch (Exception e) {
            log.warn("Redis rate-limit set failed (falling back to in-memory): {}", e.getMessage());
        }
        // In-memory (guaranteed)
        localRateLimitCache.put(rlKey, LocalDateTime.now());
    }

    // =========================================================================
    // Redis cache helpers
    // =========================================================================

    private AIReportResponse readFromCache(String key) {
        try {
            Object raw = redisTemplate.opsForValue().get(key);
            if (raw == null) return null;
            return objectMapper.convertValue(raw, AIReportResponse.class);
        } catch (Exception e) {
            log.warn("Report cache read failed for key '{}': {}", key, e.getMessage());
            return null;
        }
    }

    private void writeToCache(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Report cache write failed for key '{}': {}", key, e.getMessage());
        }
    }

    // =========================================================================
    // Key builders
    // =========================================================================

    private String buildCacheKey(UUID userId) {
        return REPORT_CACHE_PREFIX + userId + ":" + isoWeekTag();
    }

    private String buildRateLimitKey(UUID userId) {
        return REGEN_RL_PREFIX + userId + ":" + LocalDate.now();
    }

    /** E.g. {@code "2026-W21"} — unique per user per ISO week. */
    private String isoWeekTag() {
        LocalDate today = LocalDate.now();
        int week = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = today.get(IsoFields.WEEK_BASED_YEAR);
        return year + "-W" + String.format("%02d", week);
    }
}

