package com.tmapi.dto;

import com.tmapi.domain.WorkspaceMember.WorkspaceRole;
import jakarta.validation.constraints.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorkspaceDtos {

    private WorkspaceDtos() {}

    public record CreateWorkspaceRequest(
        @NotBlank @Size(max = 128)                     String name,
        @NotBlank @Size(max = 128) @Pattern(
            regexp = "^[a-z0-9-]+$",
            message = "Slug must be lowercase alphanumeric with hyphens"
        )                                              String slug,
        @Size(max = 512)                               String description
    ) {}

    public record UpdateWorkspaceRequest(
        @Size(max = 128)                               String name,
        @Size(max = 512)                               String description
    ) {}

    public record WorkspaceResponse(
        UUID    id,
        String  name,
        String  slug,
        String  description,
        UserSummary owner,
        int     memberCount,
        Instant createdAt
    ) implements Serializable {}

    public record WorkspaceDetailResponse(
        UUID              id,
        String            name,
        String            slug,
        String            description,
        UserSummary       owner,
        List<MemberResponse> members,
        Instant           createdAt,
        Instant           updatedAt
    ) implements Serializable {}

    public record InviteMemberRequest(
        @NotNull UUID           userId,
        @NotNull WorkspaceRole  role
    ) {}

    public record UpdateMemberRoleRequest(
        @NotNull WorkspaceRole role
    ) {}

    public record MemberResponse(
        UUID           id,
        UserSummary    user,
        WorkspaceRole  role,
        Instant        joinedAt
    ) implements Serializable {}
}
