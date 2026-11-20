package com.example.Employee_Management.service;

import com.example.Employee_Management.dto.DashboardStats;
import com.example.Employee_Management.dto.EmployeeResponse;
import com.example.Employee_Management.model.EmployeeStatus;
import com.example.Employee_Management.repository.DepartmentRepository;
import com.example.Employee_Management.repository.EmployeeRepository;
import com.example.Employee_Management.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final DepartmentService departmentService;

    public DashboardService(EmployeeRepository employeeRepository, DepartmentRepository departmentRepository,
                            RoleRepository roleRepository, DepartmentService departmentService) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        this.departmentService = departmentService;
    }

    public DashboardStats stats() {
        List<DashboardStats.Count> perDepartment = departmentService.findAll().stream()
                .map(d -> new DashboardStats.Count(d.id(), d.name(), d.employeeCount()))
                .sorted(Comparator.comparingLong(DashboardStats.Count::count).reversed())
                .toList();
        List<EmployeeResponse> recent = employeeRepository.findTop5ByOrderByCreatedAtDescIdDesc().stream()
                .map(EmployeeResponse::from)
                .toList();
        return new DashboardStats(
                employeeRepository.count(),
                employeeRepository.countByStatus(EmployeeStatus.ACTIVE),
                employeeRepository.countByStatus(EmployeeStatus.ON_LEAVE),
                employeeRepository.countByStatus(EmployeeStatus.INACTIVE),
                departmentRepository.count(),
                roleRepository.count(),
                perDepartment,
                recent);
    }
}
