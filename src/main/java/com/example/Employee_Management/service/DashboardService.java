package com.example.Employee_Management.service;

import com.example.Employee_Management.dto.DashboardStats;
import com.example.Employee_Management.dto.EmployeeResponse;
import com.example.Employee_Management.model.EmployeeStatus;
import com.example.Employee_Management.repository.DepartmentRepository;
import com.example.Employee_Management.repository.EmployeeRepository;
import com.example.Employee_Management.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final DepartmentService departmentService;
    private final AuditService auditService;
    private final Clock clock;

    public DashboardService(EmployeeRepository employeeRepository, DepartmentRepository departmentRepository,
                            RoleRepository roleRepository, DepartmentService departmentService,
                            AuditService auditService) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        this.departmentService = departmentService;
        this.auditService = auditService;
        this.clock = Clock.systemDefaultZone();
    }

    public DashboardStats stats() {
        LocalDate today = LocalDate.now(clock);

        Map<Long, BigDecimal> avgByDept = new HashMap<>();
        for (Object[] row : employeeRepository.averageSalaryPerDepartment(EmployeeStatus.INACTIVE)) {
            avgByDept.put((Long) row[0], round(toBigDecimal(row[1])));
        }
        List<DashboardStats.Count> perDepartment = departmentService.findAll().stream()
                .map(d -> new DashboardStats.Count(d.id(), d.name(), d.employeeCount(), avgByDept.get(d.id())))
                .sorted(Comparator.comparingLong(DashboardStats.Count::count).reversed())
                .toList();

        // Hires per month for the last 12 months (including the current one).
        YearMonth first = YearMonth.from(today).minusMonths(11);
        Map<YearMonth, Long> hires = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) {
            hires.put(first.plusMonths(i), 0L);
        }
        long last30 = 0;
        for (LocalDate joined : employeeRepository.findJoiningDatesSince(first.atDay(1))) {
            hires.computeIfPresent(YearMonth.from(joined), (k, v) -> v + 1);
            if (!joined.isBefore(today.minusDays(30))) last30++;
        }
        List<DashboardStats.MonthCount> hiresByMonth = hires.entrySet().stream()
                .map(e -> new DashboardStats.MonthCount(e.getKey().toString(),
                        e.getKey().format(MONTH_LABEL), e.getValue()))
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
                round(employeeRepository.sumSalaryExcludingStatus(EmployeeStatus.INACTIVE)),
                round(toBigDecimal(employeeRepository.averageSalaryExcludingStatus(EmployeeStatus.INACTIVE))),
                last30,
                perDepartment,
                hiresByMonth,
                recent,
                auditService.recent());
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }

    private static BigDecimal round(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
