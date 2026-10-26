package com.tmapi.repository;

import com.tmapi.domain.Task;
import com.tmapi.domain.Task.TaskPriority;
import com.tmapi.domain.Task.TaskStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Criteria API Specifications for composable, type-safe task filtering.
 *
 * Replaces query-method explosion: instead of findByWorkspaceIdAndStatusAndPriorityAndAssigneeId(...)
 * we compose individual specs with and() chains. This enables runtime filter construction
 * from optional API query parameters without any SQL string manipulation.
 *
 * Each specification translates directly to a SQL predicate, and the JPA provider
 * (Hibernate) will use the underlying indexes for efficient execution.
 */
public final class TaskSpecification {

    private TaskSpecification() {}

    /** Filter by workspace — always required for tenant isolation */
    public static Specification<Task> inWorkspace(UUID workspaceId) {
        return (root, query, cb) ->
            cb.equal(root.get("workspace").get("id"), workspaceId);
    }

    /** Filter by status */
    public static Specification<Task> hasStatus(TaskStatus status) {
        return (root, query, cb) ->
            cb.equal(root.get("status"), status);
    }

    /** Filter by priority */
    public static Specification<Task> hasPriority(TaskPriority priority) {
        return (root, query, cb) ->
            cb.equal(root.get("priority"), priority);
    }

    /** Filter by assignee */
    public static Specification<Task> assignedTo(UUID assigneeId) {
        return (root, query, cb) ->
            cb.equal(root.get("assignee").get("id"), assigneeId);
    }

    /** Filter by creator */
    public static Specification<Task> createdBy(UUID creatorId) {
        return (root, query, cb) ->
            cb.equal(root.get("creator").get("id"), creatorId);
    }

    /** Filter tasks with deadline before a given instant */
    public static Specification<Task> deadlineBefore(Instant deadline) {
        return (root, query, cb) ->
            cb.lessThanOrEqualTo(root.get("deadline"), deadline);
    }

    /** Filter tasks with deadline after a given instant */
    public static Specification<Task> deadlineAfter(Instant deadline) {
        return (root, query, cb) ->
            cb.greaterThanOrEqualTo(root.get("deadline"), deadline);
    }

    /** Case-insensitive partial title search */
    public static Specification<Task> titleContains(String keyword) {
        return (root, query, cb) ->
            cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%");
    }

    /**
     * Composite builder: assembles all non-null filters from the request into
     * a single AND-chained predicate. This is the primary entry point used by TaskService.
     */
    public static Specification<Task> buildFilter(
        UUID workspaceId,
        TaskStatus status,
        TaskPriority priority,
        UUID assigneeId,
        UUID creatorId,
        String keyword,
        Instant deadlineFrom,
        Instant deadlineTo
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Workspace isolation — always applied
            predicates.add(cb.equal(root.get("workspace").get("id"), workspaceId));

            if (status      != null) predicates.add(cb.equal(root.get("status"), status));
            if (priority    != null) predicates.add(cb.equal(root.get("priority"), priority));
            if (assigneeId  != null) predicates.add(cb.equal(root.get("assignee").get("id"), assigneeId));
            if (creatorId   != null) predicates.add(cb.equal(root.get("creator").get("id"), creatorId));
            if (keyword     != null) predicates.add(cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%"));
            if (deadlineFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("deadline"), deadlineFrom));
            if (deadlineTo  != null) predicates.add(cb.lessThanOrEqualTo(root.get("deadline"), deadlineTo));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
