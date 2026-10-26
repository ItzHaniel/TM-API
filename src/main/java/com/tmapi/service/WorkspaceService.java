package com.tmapi.service;

import com.tmapi.config.RedisConfig.CacheNames;
import com.tmapi.domain.*;
import com.tmapi.domain.WorkspaceMember.WorkspaceRole;
import com.tmapi.dto.UserSummary;
import com.tmapi.dto.WorkspaceDtos.*;
import com.tmapi.exception.ConflictException;
import com.tmapi.exception.ForbiddenException;
import com.tmapi.exception.NotFoundException;
import com.tmapi.repository.UserRepository;
import com.tmapi.repository.WorkspaceMemberRepository;
import com.tmapi.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Workspace management service with Redis caching and workspace-scoped RBAC.
 *
 * Cache strategy:
 *   - @Cacheable on reads: workspace data is expensive to load with members
 *   - @CacheEvict on writes: keeps cache consistent after mutations
 *
 * RBAC enforcement:
 *   - @PreAuthorize uses workspace-scoped authorities from JWT claims
 *   - No session state required — authority is re-derived from the token per request
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;

    // ─── Read Operations (Cached) ──────────────────────────────────────────────

    @Cacheable(value = CacheNames.WORKSPACES, key = "#workspaceId")
    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspace(UUID workspaceId) {
        Workspace ws = findWorkspaceOrThrow(workspaceId);
        return toWorkspaceResponse(ws);
    }

    @Cacheable(value = CacheNames.WORKSPACE_MEMBERS, key = "#workspaceId")
    @Transactional(readOnly = true)
    public WorkspaceDetailResponse getWorkspaceWithMembers(UUID workspaceId) {
        Workspace ws = workspaceRepository.findByIdWithMembers(workspaceId)
            .orElseThrow(() -> new NotFoundException("Workspace not found: " + workspaceId));
        return toDetailResponse(ws);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> getMyWorkspaces(UUID userId) {
        return workspaceRepository.findAllByMemberUserId(userId)
            .stream()
            .map(this::toWorkspaceResponse)
            .toList();
    }

    // ─── Write Operations (Cache-Evicting) ────────────────────────────────────

    @Transactional
    public WorkspaceResponse createWorkspace(CreateWorkspaceRequest request, UUID ownerId) {
        if (workspaceRepository.existsBySlug(request.slug())) {
            throw new ConflictException("Workspace slug already taken: " + request.slug());
        }

        User owner = userRepository.findById(ownerId)
            .orElseThrow(() -> new NotFoundException("User not found: " + ownerId));

        Workspace workspace = Workspace.builder()
            .name(request.name())
            .slug(request.slug())
            .description(request.description())
            .owner(owner)
            .build();

        workspaceRepository.save(workspace);

        // Auto-add owner as OWNER member
        WorkspaceMember ownerMembership = WorkspaceMember.builder()
            .workspace(workspace)
            .user(owner)
            .role(WorkspaceRole.OWNER)
            .build();
        memberRepository.save(ownerMembership);

        log.info("Created workspace '{}' (id={}) by user {}", workspace.getSlug(), workspace.getId(), ownerId);
        return toWorkspaceResponse(workspace);
    }

    @CacheEvict(value = {CacheNames.WORKSPACES, CacheNames.WORKSPACE_MEMBERS}, key = "#workspaceId")
    @PreAuthorize("hasAnyRole('ROLE_WS_' + #workspaceId + '_OWNER', 'ROLE_WS_' + #workspaceId + '_ADMIN')")
    @Transactional
    public WorkspaceResponse updateWorkspace(UUID workspaceId, UpdateWorkspaceRequest request) {
        Workspace ws = findWorkspaceOrThrow(workspaceId);

        if (request.name()        != null) ws.setName(request.name());
        if (request.description() != null) ws.setDescription(request.description());

        workspaceRepository.save(ws);
        return toWorkspaceResponse(ws);
    }

    @CacheEvict(value = {CacheNames.WORKSPACES, CacheNames.WORKSPACE_MEMBERS}, key = "#workspaceId")
    @PreAuthorize("hasRole('ROLE_WS_' + #workspaceId + '_OWNER')")
    @Transactional
    public void deleteWorkspace(UUID workspaceId, UUID requesterId) {
        Workspace ws = findWorkspaceOrThrow(workspaceId);
        workspaceRepository.delete(ws);
        log.info("Workspace {} deleted by user {}", workspaceId, requesterId);
    }

    // ─── Member Management ────────────────────────────────────────────────────

    @CacheEvict(value = CacheNames.WORKSPACE_MEMBERS, key = "#workspaceId")
    @PreAuthorize("hasAnyRole('ROLE_WS_' + #workspaceId + '_OWNER', 'ROLE_WS_' + #workspaceId + '_ADMIN')")
    @Transactional
    public MemberResponse inviteMember(UUID workspaceId, InviteMemberRequest request) {
        Workspace workspace = findWorkspaceOrThrow(workspaceId);
        User user = userRepository.findById(request.userId())
            .orElseThrow(() -> new NotFoundException("User not found: " + request.userId()));

        if (memberRepository.existsByWorkspaceIdAndUserId(workspaceId, request.userId())) {
            throw new ConflictException("User is already a member of this workspace");
        }

        // Cannot grant OWNER role through invite
        if (request.role() == WorkspaceRole.OWNER) {
            throw new ForbiddenException("Cannot assign OWNER role through invitation");
        }

        WorkspaceMember member = WorkspaceMember.builder()
            .workspace(workspace)
            .user(user)
            .role(request.role())
            .build();

        memberRepository.save(member);
        return toMemberResponse(member);
    }

    @CacheEvict(value = CacheNames.WORKSPACE_MEMBERS, key = "#workspaceId")
    @PreAuthorize("hasAnyRole('ROLE_WS_' + #workspaceId + '_OWNER', 'ROLE_WS_' + #workspaceId + '_ADMIN')")
    @Transactional
    public MemberResponse updateMemberRole(UUID workspaceId, UUID memberId, UpdateMemberRoleRequest request) {
        WorkspaceMember member = memberRepository.findById(memberId)
            .orElseThrow(() -> new NotFoundException("Member not found: " + memberId));

        if (!member.getWorkspace().getId().equals(workspaceId)) {
            throw new ForbiddenException("Member does not belong to this workspace");
        }
        if (member.getRole() == WorkspaceRole.OWNER) {
            throw new ForbiddenException("Cannot change OWNER role");
        }
        if (request.role() == WorkspaceRole.OWNER) {
            throw new ForbiddenException("Cannot assign OWNER role");
        }

        member.setRole(request.role());
        memberRepository.save(member);
        return toMemberResponse(member);
    }

    @CacheEvict(value = CacheNames.WORKSPACE_MEMBERS, key = "#workspaceId")
    @PreAuthorize("hasAnyRole('ROLE_WS_' + #workspaceId + '_OWNER', 'ROLE_WS_' + #workspaceId + '_ADMIN')")
    @Transactional
    public void removeMember(UUID workspaceId, UUID memberId) {
        WorkspaceMember member = memberRepository.findById(memberId)
            .orElseThrow(() -> new NotFoundException("Member not found: " + memberId));

        if (!member.getWorkspace().getId().equals(workspaceId)) {
            throw new ForbiddenException("Member does not belong to this workspace");
        }
        if (member.getRole() == WorkspaceRole.OWNER) {
            throw new ForbiddenException("Cannot remove workspace owner");
        }

        memberRepository.delete(member);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    public void assertMembership(UUID workspaceId, UUID userId) {
        if (!memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new ForbiddenException("You are not a member of workspace: " + workspaceId);
        }
    }

    private Workspace findWorkspaceOrThrow(UUID id) {
        return workspaceRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Workspace not found: " + id));
    }

    private WorkspaceResponse toWorkspaceResponse(Workspace ws) {
        return new WorkspaceResponse(
            ws.getId(), ws.getName(), ws.getSlug(), ws.getDescription(),
            toUserSummary(ws.getOwner()),
            ws.getMembers().size(),
            ws.getCreatedAt()
        );
    }

    private WorkspaceDetailResponse toDetailResponse(Workspace ws) {
        List<MemberResponse> members = ws.getMembers().stream()
            .map(this::toMemberResponse)
            .toList();
        return new WorkspaceDetailResponse(
            ws.getId(), ws.getName(), ws.getSlug(), ws.getDescription(),
            toUserSummary(ws.getOwner()), members, ws.getCreatedAt(), ws.getUpdatedAt()
        );
    }

    private MemberResponse toMemberResponse(WorkspaceMember m) {
        return new MemberResponse(m.getId(), toUserSummary(m.getUser()), m.getRole(), m.getJoinedAt());
    }

    private UserSummary toUserSummary(User u) {
        return new UserSummary(u.getId(), u.getUsername(), u.getEmail(), u.getFullName());
    }
}
