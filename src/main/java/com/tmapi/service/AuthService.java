package com.tmapi.service;

import com.tmapi.domain.User;
import com.tmapi.dto.AuthDtos.*;
import com.tmapi.exception.ConflictException;
import com.tmapi.repository.UserRepository;
import com.tmapi.security.JwtTokenProvider;
import com.tmapi.security.TmApiUserDetails;
import com.tmapi.security.TmApiUserDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles registration, login, and token refresh.
 * Stateless: no session or token store — refresh tokens are validated by JWT signature alone.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final TmApiUserDetailsService userDetailsService;

    @Value("${app.jwt.expiration-ms:3600000}")
    private long accessTokenExpirationMs;

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered: " + request.email());
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already taken: " + request.username());
        }

        User user = User.builder()
            .username(request.username())
            .email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .fullName(request.fullName())
            .build();

        userRepository.save(user);
        log.info("Registered new user: {}", request.email());

        // Auto-login after registration
        return generateTokens(request.email(), request.password());
    }

    public TokenResponse login(LoginRequest request) {
        return generateTokens(request.email(), request.password());
    }

    public TokenResponse refresh(RefreshRequest request) {
        String email = jwtTokenProvider.extractEmail(request.refreshToken());
        TmApiUserDetails userDetails = (TmApiUserDetails) userDetailsService.loadUserByUsername(email);

        if (!jwtTokenProvider.isTokenValid(request.refreshToken(), userDetails)) {
            throw new com.tmapi.exception.UnauthorizedException("Invalid or expired refresh token");
        }

        String newAccess  = jwtTokenProvider.generateAccessToken(userDetails);
        String newRefresh = jwtTokenProvider.generateRefreshToken(userDetails);
        return TokenResponse.of(newAccess, newRefresh, accessTokenExpirationMs);
    }

    private TokenResponse generateTokens(String email, String password) {
        Authentication auth = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(email, password)
        );
        TmApiUserDetails userDetails = (TmApiUserDetails) auth.getPrincipal();
        String access  = jwtTokenProvider.generateAccessToken(userDetails);
        String refresh = jwtTokenProvider.generateRefreshToken(userDetails);
        return TokenResponse.of(access, refresh, accessTokenExpirationMs);
    }
}
