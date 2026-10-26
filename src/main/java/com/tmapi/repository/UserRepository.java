package com.tmapi.repository;

import com.tmapi.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    /**
     * Single JOIN FETCH to load user + workspace memberships in one query.
     * Avoids N+1 during UserDetailsService.loadUserByUsername().
     */
    @Query("""
        SELECT DISTINCT u FROM User u
        LEFT JOIN FETCH u.workspaceMemberships wm
        LEFT JOIN FETCH wm.workspace
        WHERE u.email = :email
    """)
    Optional<User> findByEmailWithMemberships(@Param("email") String email);
}
