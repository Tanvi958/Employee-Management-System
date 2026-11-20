package com.example.Employee_Management.dto;

import java.util.List;

public record DashboardStats(
        long totalEmployees,
        long activeEmployees,
        long onLeaveEmployees,
        long inactiveEmployees,
        long departments,
        long roles,
        List<Count> perDepartment,
        List<EmployeeResponse> recentlyAdded
) {
    public record Count(Long id, String name, long count) {
    }
}
