package com.queryai.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class UserDtos {
    private UserDtos() {
    }

    public static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_MESSAGE = "Password must be 8-72 characters and contain at least one letter and one digit";

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String password,
            @NotBlank @Size(min = 2, max = 120) String fullName) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record UserDto(Long id, String email, String fullName, String jobTitle, SystemRole systemRole,
                          boolean enabled, Instant createdAt, Instant lastLoginAt) {
        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getJobTitle(), u.getSystemRole(),
                    u.isEnabled(), u.getCreatedAt(), u.getLastLoginAt());
        }
    }

    public record AuthResponse(String token, Instant expiresAt, UserDto user) {
    }

    public record UpdateProfileRequest(@NotBlank @Size(min = 2, max = 120) String fullName,
                                       @Size(max = 120) String jobTitle) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String newPassword) {
    }
}
