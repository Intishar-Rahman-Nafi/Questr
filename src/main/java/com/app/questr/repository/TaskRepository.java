package com.app.questr.repository;

import com.app.questr.model.entity.Task;
import com.app.questr.model.enums.TaskCategory;
import com.app.questr.model.enums.TaskPriority;
import com.app.questr.model.projection.CategoryBreakdownProjection;
import com.app.questr.model.projection.DailyCompletionProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    // ── Basic finders ─────────────────────────────────────────────────────

    List<Task>         findByUserId(UUID userId);
    Page<Task>         findByUserId(UUID userId, Pageable pageable);

    List<Task>         findByUserIdAndCompleted(UUID userId, Boolean completed);
    List<Task>         findByUserIdAndCategory(UUID userId, TaskCategory category);
    List<Task>         findByUserIdAndPriority(UUID userId, TaskPriority priority);

    Page<Task>         findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Page<Task>         findByUserIdAndCompletedOrderByCreatedAtDesc(
                           UUID userId, Boolean completed, Pageable pageable);

    // ── Counts (used by DashboardService & GamificationService) ──────────

    long countByUserId(UUID userId);
    long countByUserIdAndCompletedTrue(UUID userId);

    // ── Analytics queries (used by DashboardService) ──────────────────────

    /**
     * How many tasks were completed per calendar day for the given user,
     * starting from {@code weekStart}.
     * Returns native SQL result mapped via the {@link DailyCompletionProjection}
     * interface projection.
     */
    @Query(value = """
        SELECT DATE(completed_at)  AS day,
               COUNT(*)            AS count
        FROM   tasks
        WHERE  user_id     = :userId
        AND    completed   = true
        AND    completed_at >= :weekStart
        GROUP  BY DATE(completed_at)
        ORDER  BY day
        """, nativeQuery = true)
    List<DailyCompletionProjection> getWeeklyCompletions(
        @Param("userId")    UUID userId,
        @Param("weekStart") LocalDateTime weekStart);

    /**
     * Count of completed tasks per category for the given user.
     * Used for the category donut chart.
     */
    @Query(value = """
        SELECT category   AS category,
               COUNT(*)   AS count
        FROM   tasks
        WHERE  user_id  = :userId
        AND    completed = true
        AND    category IS NOT NULL
        GROUP  BY category
        ORDER  BY count DESC
        """, nativeQuery = true)
    List<CategoryBreakdownProjection> getCategoryBreakdown(@Param("userId") UUID userId);

    /**
     * All tasks (completed and incomplete) created in the current week,
     * used by {@code OpenAIService} to generate the weekly report.
     */
    @Query("SELECT t FROM Task t WHERE t.user.id = :userId AND t.createdAt >= :weekStart")
    List<Task> findTasksForWeek(
        @Param("userId")    UUID userId,
        @Param("weekStart") LocalDateTime weekStart);
}

