package com.example.Employee_Management.dto;

import com.example.Employee_Management.model.AppUser;
import com.example.Employee_Management.model.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class UserDto {

    private UserDto() {
    }

    public record LoginRequest(
            @NotBlank(message = "Username is required") String username,
            @NotBlank(message = "Password is required") String password
    ) {
    }

    public record LoginResponse(String token, Instant expiresAt, Response user) {
    }

    /** Body of POST /api/v1/users. */
    public record CreateRequest(
            @NotBlank(message = "Username is required")
            @Pattern(regexp = "^[a-zA-Z0-9._-]{3,50}$", message = "Username must be 3-50 letters, digits, dots, dashes or underscores")
            String username,

            @NotBlank(message = "Full name is required")
            @Size(max = 100, message = "Full name must be at most 100 characters")
            String fullName,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
            String password,

            @NotNull(message = "Role is required")
            UserRole role
    ) {
    }

    /** Body of PUT /api/v1/users/{id}. A blank password keeps the current one. */
    public record UpdateRequest(
            @NotBlank(message = "Full name is required")
            @Size(max = 100, message = "Full name must be at most 100 characters")
            String fullName,

            @Pattern(regexp = "^$|^.{8,72}$", message = "Password must be 8-72 characters")
            String password,

            @NotNull(message = "Role is required")
            UserRole role
    ) {
    }

    public record Response(Long id, String username, String fullName, UserRole role, Instant createdAt,
                           Instant lastLoginAt) {
        public static Response from(AppUser u) {
            return new Response(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.getCreatedAt(),
                    u.getLastLoginAt());
        }
    }
}
