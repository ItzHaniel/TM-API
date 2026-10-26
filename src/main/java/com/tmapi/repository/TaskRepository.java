package com.tmapi.repository;

import com.tmapi.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JpaSpecificationExecutor enables dynamic, composable filter queries
 * via TaskSpecification — replaces the combinatorial explosion of derived query methods.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    /**
     * Fetch a task by ID + workspaceId to enforce tenant isolation at the query level.
     * Prevents cross-workspace data leakage even if the task ID is known.
     */
    Optional<Task> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    /**
     * Used by the deadline scheduler to find tasks whose deadline is approaching
     * and whose notification has not yet been sent.
     * Runs on the composite index: workspace_id + status + deadline.
     */
    @Query("""
        SELECT t FROM Task t
        WHERE t.deadline BETWEEN :from AND :to
          AND t.deadlineNotified = false
          AND t.status NOT IN ('DONE', 'CANCELLED')
    """)
    List<Task> findPendingDeadlineNotifications(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * Bulk-mark tasks as notified after dispatching deadline notifications.
     * Single UPDATE avoids N individual saves and is safe with optimistic locking
     * because we only flip a flag (not a version-controlled state transition).
     */
    @Modifying
    @Query("""
        UPDATE Task t SET t.deadlineNotified = true
        WHERE t.id IN :ids
    """)
    void markDeadlineNotified(@Param("ids") List<UUID> ids);
}
