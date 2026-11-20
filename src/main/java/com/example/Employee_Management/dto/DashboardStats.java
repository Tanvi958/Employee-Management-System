package com.example.Employee_Management.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardStats(
        long totalEmployees,
        long activeEmployees,
        long onLeaveEmployees,
        long inactiveEmployees,
        long departments,
        long roles,
        /** Sum of monthly salaries of everyone not inactive. */
        BigDecimal monthlyPayroll,
        /** Average monthly salary of everyone not inactive (null when nobody has a salary). */
        BigDecimal averageSalary,
        long hiresLast30Days,
        List<Count> perDepartment,
        /** Employees who joined in each of the last 12 months, oldest first. */
        List<MonthCount> hiresByMonth,
        List<EmployeeResponse> recentlyAdded,
        List<AuditEntry> recentActivity
) {
    public record Count(Long id, String name, long count, BigDecimal averageSalary) {
    }

    /** {@code month} is "2026-03"; {@code label} is "Mar 2026". */
    public record MonthCount(String month, String label, long count) {
    }
}
