package com.tmapi.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Core Task entity with optimistic locking (@Version) to prevent race conditions
 * during concurrent state transitions (e.g., two background threads completing the same task).
 *
 * Indexed on workspace_id + status + deadline for high-throughput filtered queries.
 */
@Entity
@Table(
    name = "tasks",
    indexes = {
        @Index(name = "idx_tasks_workspace_id",   columnList = "workspace_id"),
        @Index(name = "idx_tasks_assignee_id",    columnList = "assignee_id"),
        @Index(name = "idx_tasks_status",          columnList = "status"),
        @Index(name = "idx_tasks_priority",        columnList = "priority"),
        @Index(name = "idx_tasks_deadline",        columnList = "deadline"),
        // Composite: common hot-path query filter
        @Index(name = "idx_tasks_ws_status_dl",   columnList = "workspace_id,status,deadline"),
        @Index(name = "idx_tasks_ws_assignee",     columnList = "workspace_id,assignee_id")
    }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    @Builder.Default
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Column(name = "deadline")
    private Instant deadline;

    /**
     * Optimistic locking: prevents lost-update race conditions when concurrent
     * threads or requests attempt to transition the same task's state simultaneously.
     * Hibernate will throw OptimisticLockException on stale-version writes.
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false, foreignKey = @ForeignKey(name = "fk_task_workspace"))
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", foreignKey = @ForeignKey(name = "fk_task_creator"))
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id", foreignKey = @ForeignKey(name = "fk_task_assignee"))
    private User assignee;

    @Column(name = "deadline_notified", nullable = false)
    @Builder.Default
    private boolean deadlineNotified = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum TaskStatus {
        TODO, IN_PROGRESS, IN_REVIEW, DONE, CANCELLED
    }

    public enum TaskPriority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
