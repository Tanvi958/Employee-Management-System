package com.example.Employee_Management.service;

import com.example.Employee_Management.dto.EmployeeRequest;
import com.example.Employee_Management.dto.EmployeeResponse;
import com.example.Employee_Management.dto.PageResponse;
import com.example.Employee_Management.exception.ConflictException;
import com.example.Employee_Management.exception.ResourceNotFoundException;
import com.example.Employee_Management.model.Department;
import com.example.Employee_Management.model.Employee;
import com.example.Employee_Management.model.EmployeeStatus;
import com.example.Employee_Management.model.Role;
import com.example.Employee_Management.repository.DepartmentRepository;
import com.example.Employee_Management.repository.EmployeeRepository;
import com.example.Employee_Management.repository.EmployeeSpecifications;
import com.example.Employee_Management.repository.RoleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class EmployeeService {

    /** Fields the client may sort by, mapped to entity paths. Anything else falls back to id. */
    private static final Map<String, String> SORTABLE = Map.of(
            "id", "id",
            "firstName", "firstName",
            "lastName", "lastName",
            "emailId", "emailId",
            "salary", "salary",
            "joiningDate", "joiningDate",
            "status", "status",
            "createdAt", "createdAt");

    public static final int MAX_PAGE_SIZE = 100;

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;

    public EmployeeService(EmployeeRepository employeeRepository,
                           DepartmentRepository departmentRepository,
                           RoleRepository roleRepository) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
    }

    public PageResponse<EmployeeResponse> search(String q, Long departmentId, Long roleId, EmployeeStatus status,
                                                 int page, int size, String sortBy, String direction) {
        List<Specification<Employee>> filters = new ArrayList<>();
        filters.add(EmployeeSpecifications.matchesText(q));
        filters.add(EmployeeSpecifications.inDepartment(departmentId));
        filters.add(EmployeeSpecifications.hasRole(roleId));
        filters.add(EmployeeSpecifications.hasStatus(status));
        Specification<Employee> spec = Specification.allOf(filters.stream().filter(Objects::nonNull).toList());

        String property = SORTABLE.getOrDefault(sortBy, "id");
        Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(dir, property).and(Sort.by(Sort.Direction.ASC, "id"));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), sort);

        return PageResponse.from(employeeRepository.findAll(spec, pageable).map(EmployeeResponse::from));
    }

    public EmployeeResponse findById(Long id) {
        return EmployeeResponse.from(getOrThrow(id));
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        String email = normaliseEmail(request.emailId());
        if (employeeRepository.existsByEmailIdIgnoreCase(email)) {
            throw new ConflictException("An employee with email " + email + " already exists");
        }
        Employee employee = new Employee();
        apply(employee, request, email);
        return EmployeeResponse.from(employeeRepository.save(employee));
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest request) {
        Employee employee = getOrThrow(id);
        String email = normaliseEmail(request.emailId());
        if (employeeRepository.existsByEmailIdIgnoreCaseAndIdNot(email, id)) {
            throw new ConflictException("An employee with email " + email + " already exists");
        }
        apply(employee, request, email);
        return EmployeeResponse.from(employeeRepository.saveAndFlush(employee));
    }

    @Transactional
    public void delete(Long id) {
        employeeRepository.delete(getOrThrow(id));
    }

    private Employee getOrThrow(Long id) {
        return employeeRepository.findWithRelationsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
    }

    private void apply(Employee employee, EmployeeRequest request, String email) {
        employee.setFirstName(request.firstName().trim());
        employee.setLastName(blankToNull(request.lastName()));
        employee.setEmailId(email);
        employee.setPhone(blankToNull(request.phone()));
        employee.setSalary(request.salary());
        employee.setJoiningDate(request.joiningDate());
        employee.setStatus(request.status() == null ? EmployeeStatus.ACTIVE : request.status());
        employee.setDepartment(request.departmentId() == null ? null : findDepartment(request.departmentId()));
        employee.setRole(request.roleId() == null ? null : findRole(request.roleId()));
    }

    private Department findDepartment(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));
    }

    private Role findRole(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + id));
    }

    private static String normaliseEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
