package com.tmapi.security;

import com.tmapi.domain.User;
import com.tmapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a user and eagerly fetches workspace memberships for authority building.
 * Using a single JOIN FETCH avoids N+1 queries during authentication.
 */
@Service
@RequiredArgsConstructor
public class TmApiUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailWithMemberships(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        return new TmApiUserDetails(user);
    }
}
