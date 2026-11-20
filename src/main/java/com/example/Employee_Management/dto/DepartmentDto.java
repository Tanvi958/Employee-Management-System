package com.example.Employee_Management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class DepartmentDto {

    private DepartmentDto() {
    }

    public record Request(
            @NotBlank(message = "Department name is required")
            @Size(max = 80, message = "Department name must be at most 80 characters")
            String name,

            @Size(max = 255, message = "Description must be at most 255 characters")
            String description
    ) {
    }

    public record Response(Long id, String name, String description, long employeeCount) {
    }
}
