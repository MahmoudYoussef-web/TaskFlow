package com.taskflow.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class TaskDtos {
    private TaskDtos() {
    }

    public record CreateTaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 10000) String description,
            Priority priority,
            Instant dueDate,
            Set<@Size(max = 50) String> tags,
            UUID assigneeId) {
    }

    public record UpdateTaskRequest(
            @NotNull Long version,
            @Size(max = 200) String title,
            @Size(max = 10000) String description,
            Priority priority,
            Instant dueDate,
            boolean clearDueDate,
            Set<@Size(max = 50) String> tags,
            UUID assigneeId) {
    }

    public record StatusPatchRequest(@NotNull TaskStatus status, @NotNull Long version) {
    }

    /** Always includes version — the frontend needs it for every subsequent write. */
    public record TaskResponse(
            UUID id, String title, String description, Priority priority, TaskStatus status,
            Instant dueDate, Set<String> tags, UUID assigneeId, UUID ownerId, Long version,
            Instant createdAt, Instant updatedAt) {
        public static TaskResponse from(Task t) {
            return new TaskResponse(t.getId(), t.getTitle(), t.getDescription(), t.getPriority(),
                    t.getStatus(), t.getDueDate(), Set.copyOf(t.getTags()), t.getAssigneeId(),
                    t.getOwnerId(), t.getVersion(), t.getCreatedAt(), t.getUpdatedAt());
        }
    }
}
