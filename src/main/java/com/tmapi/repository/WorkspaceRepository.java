package com.tmapi.repository;

import com.tmapi.domain.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    Optional<Workspace> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /**
     * Fetch all workspaces the given user is a member of (any role).
     * Used to populate the user's workspace list without loading all tasks.
     */
    @Query("""
        SELECT w FROM Workspace w
        JOIN w.members m
        WHERE m.user.id = :userId
        ORDER BY w.name
    """)
    List<Workspace> findAllByMemberUserId(@Param("userId") UUID userId);

    @Query("""
        SELECT w FROM Workspace w
        LEFT JOIN FETCH w.members m
        LEFT JOIN FETCH m.user
        WHERE w.id = :id
    """)
    Optional<Workspace> findByIdWithMembers(@Param("id") UUID id);
}
