package com.tmapi.security;

import com.tmapi.domain.User;
import com.tmapi.domain.WorkspaceMember;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Spring Security UserDetails implementation that wraps the domain User.
 * Exposes workspace-scoped roles as authorities in the form ROLE_WS_{workspaceId}_{ROLE}.
 * This enables method-level @PreAuthorize to enforce per-workspace RBAC.
 */
public class TmApiUserDetails implements UserDetails {

    @Getter
    private final UUID userId;
    @Getter
    private final String email;
    private final String passwordHash;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    public TmApiUserDetails(User user) {
        this.userId       = user.getId();
        this.email        = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.enabled      = user.isEnabled();

        // Global authority: ROLE_ADMIN or ROLE_USER
        var auths = new java.util.ArrayList<GrantedAuthority>();
        auths.add(new SimpleGrantedAuthority("ROLE_" + user.getGlobalRole().name()));

        // Per-workspace authorities: ROLE_WS_{uuid}_{WORKSPACE_ROLE}
        for (WorkspaceMember membership : user.getWorkspaceMemberships()) {
            String wsAuthority = String.format("ROLE_WS_%s_%s",
                membership.getWorkspace().getId(),
                membership.getRole().name()
            );
            auths.add(new SimpleGrantedAuthority(wsAuthority));
        }

        this.authorities = List.copyOf(auths);
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword()    { return passwordHash; }
    @Override public String getUsername()    { return email; }
    @Override public boolean isEnabled()     { return enabled; }
    @Override public boolean isAccountNonExpired()   { return true; }
    @Override public boolean isAccountNonLocked()    { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
