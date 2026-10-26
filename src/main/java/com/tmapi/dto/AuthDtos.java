package com.tmapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 64)       String username,
        @NotBlank @Email @Size(max = 255)         String email,
        @NotBlank @Size(min = 8, max = 128)       String password,
        @Size(max = 128)                          String fullName
    ) {}

    public record LoginRequest(
        @NotBlank @Email                          String email,
        @NotBlank                                 String password
    ) {}

    public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long   expiresIn
    ) {
        public static TokenResponse of(String access, String refresh, long expiresIn) {
            return new TokenResponse(access, refresh, "Bearer", expiresIn);
        }
    }

    public record RefreshRequest(
        @NotBlank String refreshToken
    ) {}
}
