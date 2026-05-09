package com.app.questr.dto.task;
import com.app.questr.model.enums.TaskCategory;
import com.app.questr.model.enums.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
public record CreateTaskRequest(
    @NotBlank(message = "Title is required") @Size(max = 255) String title,
    String description,
    TaskCategory category,
    TaskPriority priority,
    LocalDateTime deadline
) {}
