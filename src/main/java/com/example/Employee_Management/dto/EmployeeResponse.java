package com.example.Employee_Management.dto;

import com.example.Employee_Management.model.Employee;
import com.example.Employee_Management.model.EmployeeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record EmployeeResponse(
        Long id,
        String firstName,
        String lastName,
        String fullName,
        String emailId,
        String phone,
        Ref department,
        Ref role,
        BigDecimal salary,
        LocalDate joiningDate,
        EmployeeStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    /** A small {id, name} reference to a department or role. */
    public record Ref(Long id, String name) {
    }

    public static EmployeeResponse from(Employee e) {
        String fullName = e.getLastName() == null || e.getLastName().isBlank()
                ? e.getFirstName()
                : e.getFirstName() + " " + e.getLastName();
        Ref department = e.getDepartment() == null ? null
                : new Ref(e.getDepartment().getId(), e.getDepartment().getName());
        Ref role = e.getRole() == null ? null
                : new Ref(e.getRole().getId(), e.getRole().getTitle());
        return new EmployeeResponse(e.getId(), e.getFirstName(), e.getLastName(), fullName, e.getEmailId(),
                e.getPhone(), department, role, e.getSalary(), e.getJoiningDate(), e.getStatus(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
