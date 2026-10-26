package com.tmapi.dto;

import com.tmapi.domain.Task.TaskPriority;
import com.tmapi.domain.Task.TaskStatus;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public final class TaskDtos {

    private TaskDtos() {}

    public record CreateTaskRequest(
        @NotBlank @Size(max = 255)   String title,
        @Size(max = 4000)            String description,
        @NotNull                     TaskPriority priority,
                                     UUID assigneeId,
        @Future                      Instant deadline
    ) {}

    public record UpdateTaskRequest(
        @Size(max = 255)             String title,
        @Size(max = 4000)            String description,
                                     TaskStatus status,
                                     TaskPriority priority,
                                     UUID assigneeId,
                                     Instant deadline
    ) {}

    public record TaskStatusTransitionRequest(
        @NotNull TaskStatus newStatus
    ) {}

    /** Serializable: stored in Redis as cache value */
    public record TaskResponse(
        UUID         id,
        String       title,
        String       description,
        TaskStatus   status,
        TaskPriority priority,
        Instant      deadline,
        Long         version,
        UUID         workspaceId,
        UserSummary  creator,
        UserSummary  assignee,
        Instant      createdAt,
        Instant      updatedAt
    ) implements Serializable {}

    public record TaskFilterParams(
        TaskStatus   status,
        TaskPriority priority,
        UUID         assigneeId,
        UUID         creatorId,
        String       keyword,
        Instant      deadlineFrom,
        Instant      deadlineTo,
        int          page,
        int          size,
        String       sortBy,
        String       sortDir
    ) {}
}
