package com.example.Employee_Management.dto;

import com.example.Employee_Management.model.EmployeeStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Body of POST / PUT /api/v1/employees. */
public record EmployeeRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 60, message = "First name must be at most 60 characters")
        String firstName,

        @Size(max = 60, message = "Last name must be at most 60 characters")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 120, message = "Email must be at most 120 characters")
        String emailId,

        @Pattern(regexp = "^$|^[+]?[0-9 ()-]{7,20}$", message = "Phone must be 7-20 digits")
        String phone,

        Long departmentId,

        Long roleId,

        @PositiveOrZero(message = "Salary cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Salary can have at most 10 digits and 2 decimals")
        BigDecimal salary,

        @PastOrPresent(message = "Joining date cannot be in the future")
        LocalDate joiningDate,

        EmployeeStatus status
) {
}
