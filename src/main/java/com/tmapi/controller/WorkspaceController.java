package com.tmapi.controller;

import com.tmapi.dto.WorkspaceDtos.*;
import com.tmapi.security.TmApiUserDetails;
import com.tmapi.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Workspace management REST API.
 * Multi-tenant isolation: membership is checked via @PreAuthorize in WorkspaceService.
 *
 * GET    /api/v1/workspaces                         — list my workspaces
 * POST   /api/v1/workspaces                         — create workspace
 * GET    /api/v1/workspaces/{id}                    — get workspace (summary)
 * GET    /api/v1/workspaces/{id}/details            — get workspace with members
 * PATCH  /api/v1/workspaces/{id}                   — update (ADMIN/OWNER)
 * DELETE /api/v1/workspaces/{id}                   — delete (OWNER only)
 * POST   /api/v1/workspaces/{id}/members            — invite member (ADMIN/OWNER)
 * PATCH  /api/v1/workspaces/{id}/members/{memberId} — update member role
 * DELETE /api/v1/workspaces/{id}/members/{memberId} — remove member
 */
@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @GetMapping
    public ResponseEntity<List<WorkspaceResponse>> listMyWorkspaces(
        @AuthenticationPrincipal TmApiUserDetails principal
    ) {
        return ResponseEntity.ok(workspaceService.getMyWorkspaces(principal.getUserId()));
    }

    @PostMapping
    public ResponseEntity<WorkspaceResponse> createWorkspace(
        @Valid @RequestBody CreateWorkspaceRequest request,
        @AuthenticationPrincipal TmApiUserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(workspaceService.createWorkspace(request, principal.getUserId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkspaceResponse> getWorkspace(@PathVariable UUID id) {
        return ResponseEntity.ok(workspaceService.getWorkspace(id));
    }

    @GetMapping("/{id}/details")
    public ResponseEntity<WorkspaceDetailResponse> getWorkspaceDetails(@PathVariable UUID id) {
        return ResponseEntity.ok(workspaceService.getWorkspaceWithMembers(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<WorkspaceResponse> updateWorkspace(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateWorkspaceRequest request
    ) {
        return ResponseEntity.ok(workspaceService.updateWorkspace(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkspace(
        @PathVariable UUID id,
        @AuthenticationPrincipal TmApiUserDetails principal
    ) {
        workspaceService.deleteWorkspace(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    // ─── Member Management ────────────────────────────────────────────────────

    @PostMapping("/{id}/members")
    public ResponseEntity<MemberResponse> inviteMember(
        @PathVariable UUID id,
        @Valid @RequestBody InviteMemberRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(workspaceService.inviteMember(id, request));
    }

    @PatchMapping("/{id}/members/{memberId}")
    public ResponseEntity<MemberResponse> updateMemberRole(
        @PathVariable UUID id,
        @PathVariable UUID memberId,
        @Valid @RequestBody UpdateMemberRoleRequest request
    ) {
        return ResponseEntity.ok(workspaceService.updateMemberRole(id, memberId, request));
    }

    @DeleteMapping("/{id}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
        @PathVariable UUID id,
        @PathVariable UUID memberId
    ) {
        workspaceService.removeMember(id, memberId);
        return ResponseEntity.noContent().build();
    }
}
