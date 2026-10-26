package com.tmapi.controller;

import com.tmapi.domain.Task.TaskPriority;
import com.tmapi.domain.Task.TaskStatus;
import com.tmapi.dto.TaskDtos.*;
import com.tmapi.security.TmApiUserDetails;
import com.tmapi.service.TaskService;
import com.tmapi.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Task REST API — scoped to a workspace (all paths include /{workspaceId}).
 * Membership check is enforced before delegating to TaskService.
 *
 * GET    /api/v1/workspaces/{wid}/tasks             — list tasks (filtered, paginated)
 * POST   /api/v1/workspaces/{wid}/tasks             — create task
 * GET    /api/v1/workspaces/{wid}/tasks/{id}        — get single task
 * PUT    /api/v1/workspaces/{wid}/tasks/{id}        — full update
 * PATCH  /api/v1/workspaces/{wid}/tasks/{id}/status — transition status
 * DELETE /api/v1/workspaces/{wid}/tasks/{id}        — delete task
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final WorkspaceService workspaceService;

    @GetMapping
    public ResponseEntity<Page<TaskResponse>> listTasks(
        @PathVariable UUID workspaceId,
        @AuthenticationPrincipal TmApiUserDetails principal,
        @RequestParam(required = false) TaskStatus    status,
        @RequestParam(required = false) TaskPriority  priority,
        @RequestParam(required = false) UUID          assigneeId,
        @RequestParam(required = false) UUID          creatorId,
        @RequestParam(required = false) String        keyword,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant deadlineFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant deadlineTo,
        @RequestParam(defaultValue = "0")             int     page,
        @RequestParam(defaultValue = "20")            int     size,
        @RequestParam(defaultValue = "createdAt")     String  sortBy,
        @RequestParam(defaultValue = "desc")          String  sortDir
    ) {
        workspaceService.assertMembership(workspaceId, principal.getUserId());

        TaskFilterParams params = new TaskFilterParams(
            status, priority, assigneeId, creatorId, keyword,
            deadlineFrom, deadlineTo, page, size, sortBy, sortDir
        );

        return ResponseEntity.ok(taskService.listTasks(workspaceId, params));
    }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
        @PathVariable UUID workspaceId,
        @Valid @RequestBody CreateTaskRequest request,
        @AuthenticationPrincipal TmApiUserDetails principal
    ) {
        workspaceService.assertMembership(workspaceId, principal.getUserId());
        TaskResponse created = taskService.createTask(workspaceId, request, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskResponse> getTask(
        @PathVariable UUID workspaceId,
        @PathVariable UUID taskId,
        @AuthenticationPrincipal TmApiUserDetails principal
    ) {
        workspaceService.assertMembership(workspaceId, principal.getUserId());
        return ResponseEntity.ok(taskService.getTask(workspaceId, taskId));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<TaskResponse> updateTask(
        @PathVariable UUID workspaceId,
        @PathVariable UUID taskId,
        @Valid @RequestBody UpdateTaskRequest request,
        @AuthenticationPrincipal TmApiUserDetails principal
    ) {
        workspaceService.assertMembership(workspaceId, principal.getUserId());
        return ResponseEntity.ok(taskService.updateTask(workspaceId, taskId, request));
    }

    @PatchMapping("/{taskId}/status")
    public ResponseEntity<TaskResponse> transitionStatus(
        @PathVariable UUID workspaceId,
        @PathVariable UUID taskId,
        @Valid @RequestBody TaskStatusTransitionRequest request,
        @AuthenticationPrincipal TmApiUserDetails principal
    ) {
        workspaceService.assertMembership(workspaceId, principal.getUserId());
        return ResponseEntity.ok(taskService.transitionStatus(workspaceId, taskId, request));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> deleteTask(
        @PathVariable UUID workspaceId,
        @PathVariable UUID taskId,
        @AuthenticationPrincipal TmApiUserDetails principal
    ) {
        workspaceService.assertMembership(workspaceId, principal.getUserId());
        taskService.deleteTask(workspaceId, taskId);
        return ResponseEntity.noContent().build();
    }
}
