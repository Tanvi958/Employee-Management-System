package com.example.Employee_Management.repository;

import com.example.Employee_Management.model.Employee;
import com.example.Employee_Management.model.EmployeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    /** Paged search; department and role are fetched in the same query to avoid N+1 selects. */
    @Override
    @EntityGraph(attributePaths = {"department", "role"})
    Page<Employee> findAll(Specification<Employee> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"department", "role"})
    Optional<Employee> findWithRelationsById(Long id);

    @EntityGraph(attributePaths = {"department", "role"})
    List<Employee> findTop5ByOrderByCreatedAtDescIdDesc();

    boolean existsByEmailIdIgnoreCase(String emailId);

    boolean existsByEmailIdIgnoreCaseAndIdNot(String emailId, Long id);

    long countByDepartmentId(Long departmentId);

    long countByRoleId(Long roleId);

    long countByStatus(EmployeeStatus status);

    @Query("SELECT e.department.id, COUNT(e) FROM Employee e WHERE e.department IS NOT NULL GROUP BY e.department.id")
    List<Object[]> countPerDepartment();

    @Query("SELECT e.role.id, COUNT(e) FROM Employee e WHERE e.role IS NOT NULL GROUP BY e.role.id")
    List<Object[]> countPerRole();
}
