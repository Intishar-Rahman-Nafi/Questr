package com.app.questr.controller;

import com.app.questr.dto.task.CreateTaskRequest;
import com.app.questr.dto.task.TaskResponse;
import com.app.questr.dto.task.UpdateTaskRequest;
import com.app.questr.model.enums.TaskCategory;
import com.app.questr.model.enums.TaskPriority;
import com.app.questr.security.UserPrincipal;
import com.app.questr.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Module 4 — Task Management REST API.
 *
 * <p>All endpoints require a valid JWT Bearer token.
 * The authenticated user's ID is resolved from {@link UserPrincipal}.
 *
 * <pre>
 * GET    /api/v1/tasks                   - paginated task list (with optional filters)
 * POST   /api/v1/tasks                   - create a task
 * GET    /api/v1/tasks/{id}              - fetch single task
 * PUT    /api/v1/tasks/{id}              - update task (not completed)
 * DELETE /api/v1/tasks/{id}              - delete task
 * PATCH  /api/v1/tasks/{id}/complete     - mark task complete + award XP
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    // ── List ──────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/tasks
     *
     * <p>Optional query params:
     * <ul>
     *   <li>{@code completed}  — true | false</li>
     *   <li>{@code category}   — WORK | PERSONAL | HEALTH | LEARNING | DEV | OTHER</li>
     *   <li>{@code priority}   — LOW | MEDIUM | HIGH</li>
     *   <li>{@code page}, {@code size}, {@code sort} — standard Spring Pageable</li>
     * </ul>
     */
    @GetMapping
    public ResponseEntity<Page<TaskResponse>> listTasks(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Boolean      completed,
            @RequestParam(required = false) TaskCategory category,
            @RequestParam(required = false) TaskPriority priority,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(
                taskService.getUserTasks(principal.getId(), completed, category, priority, pageable));
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateTaskRequest req) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(taskService.createTask(principal.getId(), req));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {

        return ResponseEntity.ok(taskService.getTask(principal.getId(), id));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskRequest req) {

        return ResponseEntity.ok(taskService.updateTask(principal.getId(), id, req));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {

        taskService.deleteTask(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    // ── Complete ──────────────────────────────────────────────────────────────

    @PatchMapping("/{id}/complete")
    public ResponseEntity<TaskResponse> completeTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {

        return ResponseEntity.ok(taskService.completeTask(principal.getId(), id));
    }
}

