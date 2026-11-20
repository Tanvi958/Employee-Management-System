package com.example.Employee_Management.repository;

import com.example.Employee_Management.model.Employee;
import com.example.Employee_Management.model.EmployeeStatus;
import org.springframework.data.jpa.domain.Specification;

/** Composable filters for the employee search endpoint. Each returns null when its filter is not set. */
public final class EmployeeSpecifications {

    private EmployeeSpecifications() {
    }

    /** Case-insensitive match on first name, last name, full name or e-mail. */
    public static Specification<Employee> matchesText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String pattern = "%" + text.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            var lastName = cb.coalesce(root.<String>get("lastName"), "");
            var fullName = cb.concat(cb.concat(root.<String>get("firstName"), " "), lastName);
            return cb.or(
                    cb.like(cb.lower(fullName), pattern),
                    cb.like(cb.lower(root.get("emailId")), pattern));
        };
    }

    public static Specification<Employee> inDepartment(Long departmentId) {
        return departmentId == null ? null
                : (root, query, cb) -> cb.equal(root.get("department").get("id"), departmentId);
    }

    public static Specification<Employee> hasRole(Long roleId) {
        return roleId == null ? null
                : (root, query, cb) -> cb.equal(root.get("role").get("id"), roleId);
    }

    public static Specification<Employee> hasStatus(EmployeeStatus status) {
        return status == null ? null
                : (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
