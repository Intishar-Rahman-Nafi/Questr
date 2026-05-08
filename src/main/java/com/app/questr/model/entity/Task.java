package com.app.questr.model.entity;

import com.app.questr.model.enums.TaskCategory;
import com.app.questr.model.enums.TaskPriority;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A productivity task owned by a {@link User}.
 *
 * <p>XP is calculated at completion time in {@code TaskService.completeTask()}
 * using the formula:
 * <pre>
 *   xp = priority.baseXp
 *      + (deadline bonus if completed before deadline)
 *      + (streak bonus if active streak >= 3)
 * </pre>
 * The resolved xp is stored in {@code xpValue} so historical XP is immutable.
 */
@Entity
@Table(
    name = "tasks",
    indexes = {
        @Index(name = "idx_tasks_user_id",    columnList = "user_id"),
        @Index(name = "idx_tasks_completed",  columnList = "user_id, completed"),
        @Index(name = "idx_tasks_created_at", columnList = "user_id, created_at")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "user")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private TaskCategory category;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TaskPriority priority;

    @Column(name = "deadline")
    private LocalDateTime deadline;

    @Column(nullable = false)
    @Builder.Default
    private Boolean completed = false;

    /**
     * XP awarded on completion. Set when the task is created based on priority;
     * can be overridden by the user. Locked once the task is completed.
     */
    @Column(name = "xp_value", nullable = false)
    @Builder.Default
    private Integer xpValue = 10;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Stamped by {@code TaskService.completeTask()} — never set manually. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}

