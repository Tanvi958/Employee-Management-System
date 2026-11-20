package com.example.Employee_Management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class RoleDto {

    private RoleDto() {
    }

    public record Request(
            @NotBlank(message = "Role title is required")
            @Size(max = 80, message = "Role title must be at most 80 characters")
            String title,

            @Size(max = 255, message = "Description must be at most 255 characters")
            String description
    ) {
    }

    public record Response(Long id, String title, String description, long employeeCount) {
    }
}
