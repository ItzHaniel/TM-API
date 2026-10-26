package com.tmapi.service;

import com.tmapi.config.RedisConfig.CacheNames;
import com.tmapi.domain.Task;
import com.tmapi.domain.Task.TaskStatus;
import com.tmapi.domain.User;
import com.tmapi.domain.Workspace;
import com.tmapi.dto.TaskDtos.*;
import com.tmapi.dto.UserSummary;
import com.tmapi.exception.ConflictException;
import com.tmapi.exception.ForbiddenException;
import com.tmapi.exception.NotFoundException;
import com.tmapi.repository.TaskRepository;
import com.tmapi.repository.TaskSpecification;
import com.tmapi.repository.UserRepository;
import com.tmapi.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Core task CRUD + state machine with:
 *  - Redis caching on reads (individual task + paginated list cache invalidation)
 *  - Optimistic locking via @Version — Hibernate throws OptimisticLockingFailureException on conflict
 *  - Spec-based dynamic filtering powered by JPA Criteria API
 *  - Tenant isolation enforced at repository query level (workspaceId always scoped)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskRepository taskRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;

    /** Valid state transitions — prevents illegal status jumps */
    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS = Map.of(
        TaskStatus.TODO,        EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED),
        TaskStatus.IN_PROGRESS, EnumSet.of(TaskStatus.IN_REVIEW,   TaskStatus.TODO, TaskStatus.CANCELLED),
        TaskStatus.IN_REVIEW,   EnumSet.of(TaskStatus.DONE,        TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED),
        TaskStatus.DONE,        EnumSet.of(TaskStatus.IN_PROGRESS),
        TaskStatus.CANCELLED,   EnumSet.of(TaskStatus.TODO)
    );

    // ─── Reads ───────────────────────────────────────────────────────────────

    @Cacheable(value = CacheNames.TASKS, key = "#workspaceId + ':' + #taskId")
    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID workspaceId, UUID taskId) {
        Task task = findTaskOrThrow(workspaceId, taskId);
        return toResponse(task);
    }

    /**
     * Dynamic filtered list using JPA Specifications.
     * Paginated results are not individually cached to avoid cache fragmentation;
     * instead the task_list cache stores the most-recent page results per workspace.
     */
    @Cacheable(
        value = CacheNames.TASK_LIST,
        key   = "#workspaceId + ':' + #params.status() + ':' + #params.priority() + ':' + #params.page() + ':' + #params.size()",
        condition = "#params.keyword() == null && #params.assigneeId() == null"  // only cache simple queries
    )
    @Transactional(readOnly = true)
    public Page<TaskResponse> listTasks(UUID workspaceId, TaskFilterParams params) {
        Specification<Task> spec = TaskSpecification.buildFilter(
            workspaceId,
            params.status(),
            params.priority(),
            params.assigneeId(),
            params.creatorId(),
            params.keyword(),
            params.deadlineFrom(),
            params.deadlineTo()
        );

        Sort sort = Sort.by(
            "desc".equalsIgnoreCase(params.sortDir()) ? Sort.Direction.DESC : Sort.Direction.ASC,
            params.sortBy() != null ? params.sortBy() : "createdAt"
        );

        PageRequest pageRequest = PageRequest.of(params.page(), params.size(), sort);
        return taskRepository.findAll(spec, pageRequest).map(this::toResponse);
    }

    // ─── Writes ──────────────────────────────────────────────────────────────

    @Caching(evict = @CacheEvict(value = CacheNames.TASK_LIST, allEntries = true))
    @Transactional
    public TaskResponse createTask(UUID workspaceId, CreateTaskRequest request, UUID creatorId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
            .orElseThrow(() -> new NotFoundException("Workspace not found: " + workspaceId));
        User creator = findUserOrThrow(creatorId);

        Task.TaskBuilder builder = Task.builder()
            .title(request.title())
            .description(request.description())
            .priority(request.priority())
            .workspace(workspace)
            .creator(creator)
            .deadline(request.deadline());

        if (request.assigneeId() != null) {
            builder.assignee(findUserOrThrow(request.assigneeId()));
        }

        Task saved = taskRepository.save(builder.build());
        log.info("Created task {} in workspace {}", saved.getId(), workspaceId);
        return toResponse(saved);
    }

    @Caching(
        put  = @CachePut(value = CacheNames.TASKS, key = "#workspaceId + ':' + #taskId"),
        evict = @CacheEvict(value = CacheNames.TASK_LIST, allEntries = true)
    )
    @Transactional
    public TaskResponse updateTask(UUID workspaceId, UUID taskId, UpdateTaskRequest request) {
        Task task = findTaskOrThrow(workspaceId, taskId);

        if (request.title()       != null) task.setTitle(request.title());
        if (request.description() != null) task.setDescription(request.description());
        if (request.priority()    != null) task.setPriority(request.priority());
        if (request.deadline()    != null) task.setDeadline(request.deadline());

        if (request.assigneeId() != null) {
            task.setAssignee(findUserOrThrow(request.assigneeId()));
        }

        // Status via update path also validates state machine
        if (request.status() != null) {
            validateTransition(task.getStatus(), request.status());
            task.setStatus(request.status());
        }

        return toResponse(taskRepository.save(task));
    }

    /**
     * Dedicated status transition endpoint — uses optimistic locking.
     * @Version on Task ensures that if two concurrent requests try to change
     * the same task's status, only one succeeds; the other gets a stale-version error.
     *
     * @Retryable handles transient optimistic lock conflicts with exponential backoff.
     */
    @Retryable(
        retryFor = OptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 50, multiplier = 2)
    )
    @Caching(
        put  = @CachePut(value = CacheNames.TASKS, key = "#workspaceId + ':' + #taskId"),
        evict = @CacheEvict(value = CacheNames.TASK_LIST, allEntries = true)
    )
    @Transactional
    public TaskResponse transitionStatus(UUID workspaceId, UUID taskId, TaskStatusTransitionRequest request) {
        Task task = findTaskOrThrow(workspaceId, taskId);
        validateTransition(task.getStatus(), request.newStatus());
        task.setStatus(request.newStatus());
        log.info("Task {} status: {} → {}", taskId, task.getStatus(), request.newStatus());
        return toResponse(taskRepository.save(task));
    }

    @Caching(evict = {
        @CacheEvict(value = CacheNames.TASKS,     key = "#workspaceId + ':' + #taskId"),
        @CacheEvict(value = CacheNames.TASK_LIST, allEntries = true)
    })
    @Transactional
    public void deleteTask(UUID workspaceId, UUID taskId) {
        Task task = findTaskOrThrow(workspaceId, taskId);
        taskRepository.delete(task);
        log.info("Deleted task {} from workspace {}", taskId, workspaceId);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void validateTransition(TaskStatus current, TaskStatus next) {
        Set<TaskStatus> allowed = ALLOWED_TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(next)) {
            throw new ConflictException(
                String.format("Invalid status transition: %s → %s", current, next)
            );
        }
    }

    private Task findTaskOrThrow(UUID workspaceId, UUID taskId) {
        return taskRepository.findByIdAndWorkspaceId(taskId, workspaceId)
            .orElseThrow(() -> new NotFoundException("Task not found: " + taskId));
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }

    TaskResponse toResponse(Task t) {
        return new TaskResponse(
            t.getId(), t.getTitle(), t.getDescription(),
            t.getStatus(), t.getPriority(), t.getDeadline(), t.getVersion(),
            t.getWorkspace().getId(),
            toUserSummary(t.getCreator()),
            toUserSummary(t.getAssignee()),
            t.getCreatedAt(), t.getUpdatedAt()
        );
    }

    private UserSummary toUserSummary(User u) {
        if (u == null) return null;
        return new UserSummary(u.getId(), u.getUsername(), u.getEmail(), u.getFullName());
    }
}
