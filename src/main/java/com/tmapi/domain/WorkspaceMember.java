package com.tmapi.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Join entity between a User and a Workspace — carries the workspace-scoped role.
 * This enables per-workspace RBAC: a user can be ADMIN in workspace A and VIEWER in workspace B.
 */
@Entity
@Table(
    name = "workspace_members",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_workspace_member",
        columnNames = {"workspace_id", "user_id"}
    ),
    indexes = {
        @Index(name = "idx_wm_workspace_id", columnList = "workspace_id"),
        @Index(name = "idx_wm_user_id", columnList = "user_id")
    }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false, foreignKey = @ForeignKey(name = "fk_wm_workspace"))
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_wm_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    @Builder.Default
    private WorkspaceRole role = WorkspaceRole.MEMBER;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private Instant joinedAt;

    public enum WorkspaceRole {
        OWNER, ADMIN, MEMBER, VIEWER
    }
}
