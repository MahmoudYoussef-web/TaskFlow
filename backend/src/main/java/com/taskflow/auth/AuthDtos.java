package com.taskflow.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 120) String displayName) {
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record AuthResponse(String accessToken, String refreshToken, UserDto user) {
    }

    public record UserDto(String id, String email, String displayName, String role) {
        public static UserDto from(User u) {
            return new UserDto(u.getId().toString(), u.getEmail(), u.getDisplayName(),
                    u.getRole().name());
        }
    }
}
