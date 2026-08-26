package com.app.questr.service;

import com.app.questr.dto.task.CreateTaskRequest;
import com.app.questr.dto.task.TaskResponse;
import com.app.questr.dto.task.UpdateTaskRequest;
import com.app.questr.exception.ApiException;
import com.app.questr.exception.ResourceNotFoundException;
import com.app.questr.model.entity.Task;
import com.app.questr.model.entity.User;
import com.app.questr.model.entity.UserStats;
import com.app.questr.model.enums.TaskCategory;
import com.app.questr.model.enums.TaskPriority;
import com.app.questr.repository.TaskRepository;
import com.app.questr.repository.UserRepository;
import com.app.questr.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Task CRUD + completion logic.
 *
 * <p>XP formula on completion:
 * <ul>
 *   <li>base  = priority.baseXp   (HIGH=20, MEDIUM=10, LOW=5; default 5 if null)</li>
 *   <li>+5    if task has a deadline and it was completed before that deadline</li>
 *   <li>+3    if the user's current streak is >= 3 at the moment of completion</li>
 * </ul>
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskRepository        taskRepository;
    private final UserRepository        userRepository;
    private final UserStatsRepository   userStatsRepository;
    private final GamificationService   gamificationService;

    // ── Create ────────────────────────────────────────────────────────────────

    public TaskResponse createTask(UUID userId, CreateTaskRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        int baseXp = req.priority() != null ? req.priority().getBaseXp() : 5;

        Task task = Task.builder()
                .user(user)
                .title(req.title())
                .description(req.description())
                .category(req.category())
                .priority(req.priority())
                .deadline(req.deadline())
                .xpValue(baseXp)
                .build();

        Task saved = taskRepository.saveAndFlush(task);
        log.info("Task created: {} (user={})", saved.getId(), userId);
        return toResponse(saved);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TaskResponse> getUserTasks(
            UUID userId,
            Boolean completed,
            TaskCategory category,
            TaskPriority priority,
            Pageable pageable) {

        return taskRepository
                .findByUserIdWithFilters(userId, completed, category, priority, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID userId, UUID taskId) {
        return toResponse(findOwnedTask(userId, taskId));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    public TaskResponse updateTask(UUID userId, UUID taskId, UpdateTaskRequest req) {
        Task task = findOwnedTask(userId, taskId);
        if (Boolean.TRUE.equals(task.getCompleted())) {
            throw new ApiException("Completed tasks cannot be modified", HttpStatus.BAD_REQUEST);
        }
        if (req.title()       != null) task.setTitle(req.title());
        if (req.description() != null) task.setDescription(req.description());
        if (req.category()    != null) task.setCategory(req.category());
        if (req.priority()    != null) {
            task.setPriority(req.priority());
            // Re-align xpValue with the new priority (not yet completed)
            task.setXpValue(req.priority().getBaseXp());
        }
        if (req.deadline()    != null) task.setDeadline(req.deadline());
        return toResponse(taskRepository.save(task));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public void deleteTask(UUID userId, UUID taskId) {
        Task task = findOwnedTask(userId, taskId);
        taskRepository.delete(task);
        log.info("Task deleted: {} (user={})", taskId, userId);
    }

    // ── Complete ──────────────────────────────────────────────────────────────

    public TaskResponse completeTask(UUID userId, UUID taskId) {
        Task task = findOwnedTask(userId, taskId);
        if (Boolean.TRUE.equals(task.getCompleted())) {
            throw new ApiException("Task is already completed", HttpStatus.BAD_REQUEST);
        }

        int xp = calculateXp(task, userId);
        task.setXpValue(xp);
        task.setCompleted(true);
        task.setCompletedAt(LocalDateTime.now());
        taskRepository.save(task);

        gamificationService.awardXP(userId, xp);
        log.info("Task {} completed by user {} (+{}xp)", taskId, userId, xp);
        return toResponse(task);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Fetches task and asserts ownership, throwing appropriate HTTP exceptions. */
    private Task findOwnedTask(UUID userId, UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (!task.getUser().getId().equals(userId)) {
            throw new ApiException("You are not allowed to access this task", HttpStatus.FORBIDDEN);
        }
        return task;
    }

    /**
     * XP earned on completion:
     *   base (by priority) + deadline bonus (+5) + streak bonus (+3 if streak >= 3)
     */
    private int calculateXp(Task task, UUID userId) {
        int xp = task.getPriority() != null ? task.getPriority().getBaseXp() : 5;

        // Deadline bonus
        if (task.getDeadline() != null && LocalDateTime.now().isBefore(task.getDeadline())) {
            xp += 5;
        }

        // Streak bonus — read BEFORE awardXP updates it
        int streak = userStatsRepository.findByUserId(userId)
                .map(UserStats::getCurrentStreak)
                .orElse(0);
        if (streak >= 3) {
            xp += 3;
        }

        return xp;
    }

    private TaskResponse toResponse(Task t) {
        return new TaskResponse(
                t.getId(), t.getTitle(), t.getDescription(),
                t.getCategory(), t.getPriority(), t.getDeadline(),
                t.getCompleted(), t.getXpValue(),
                t.getCreatedAt(), t.getCompletedAt());
    }
}





