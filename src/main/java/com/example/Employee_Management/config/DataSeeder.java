package com.example.Employee_Management.config;

import com.example.Employee_Management.model.Department;
import com.example.Employee_Management.model.Employee;
import com.example.Employee_Management.model.EmployeeStatus;
import com.example.Employee_Management.model.Role;
import com.example.Employee_Management.repository.DepartmentRepository;
import com.example.Employee_Management.repository.EmployeeRepository;
import com.example.Employee_Management.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads sample departments, roles and employees on start-up when the database is empty, so the dashboard
 * has something to show. Turn it off with {@code app.seed-data=false}.
 */
@Component
@ConditionalOnProperty(name = "app.seed-data", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final EmployeeRepository employeeRepository;

    public DataSeeder(DepartmentRepository departmentRepository, RoleRepository roleRepository,
                      EmployeeRepository employeeRepository) {
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        this.employeeRepository = employeeRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (employeeRepository.count() > 0 || departmentRepository.count() > 0 || roleRepository.count() > 0) {
            return;
        }

        Map<String, Department> dept = departmentRepository.saveAll(List.of(
                new Department("Engineering", "Builds and maintains the product"),
                new Department("Human Resources", "Hiring, onboarding and people operations"),
                new Department("Finance", "Payroll, budgeting and accounts"),
                new Department("Design", "Product, UI and UX design"),
                new Department("Operations", "Internal IT and day-to-day operations")
        )).stream().collect(Collectors.toMap(Department::getName, Function.identity()));

        Map<String, Role> role = roleRepository.saveAll(List.of(
                new Role("Software Engineer", "Develops backend and frontend features"),
                new Role("Senior Software Engineer", "Leads technical design and reviews"),
                new Role("HR Executive", "Handles recruitment and employee relations"),
                new Role("Accountant", "Maintains books and processes payroll"),
                new Role("UI/UX Designer", "Designs user flows and interfaces"),
                new Role("Operations Manager", "Runs internal operations")
        )).stream().collect(Collectors.toMap(Role::getTitle, Function.identity()));

        Object[][] rows = {
                {"Aarav", "Sharma", "aarav.sharma@example.com", "+91 98100 11001", "Engineering", "Senior Software Engineer", "145000", "2022-04-11", EmployeeStatus.ACTIVE},
                {"Priya", "Verma", "priya.verma@example.com", "+91 98100 11002", "Engineering", "Software Engineer", "92000", "2023-07-03", EmployeeStatus.ACTIVE},
                {"Rohan", "Gupta", "rohan.gupta@example.com", "+91 98100 11003", "Engineering", "Software Engineer", "88000", "2024-01-15", EmployeeStatus.ON_LEAVE},
                {"Sneha", "Iyer", "sneha.iyer@example.com", "+91 98100 11004", "Design", "UI/UX Designer", "81000", "2023-09-18", EmployeeStatus.ACTIVE},
                {"Kabir", "Mehta", "kabir.mehta@example.com", "+91 98100 11005", "Finance", "Accountant", "70000", "2021-11-01", EmployeeStatus.ACTIVE},
                {"Ananya", "Singh", "ananya.singh@example.com", "+91 98100 11006", "Human Resources", "HR Executive", "65000", "2024-06-10", EmployeeStatus.ACTIVE},
                {"Vikram", "Rao", "vikram.rao@example.com", "+91 98100 11007", "Operations", "Operations Manager", "110000", "2020-02-24", EmployeeStatus.ACTIVE},
                {"Meera", "Nair", "meera.nair@example.com", "+91 98100 11008", "Engineering", "Software Engineer", "90000", "2025-03-03", EmployeeStatus.ACTIVE},
                {"Arjun", "Kapoor", "arjun.kapoor@example.com", "+91 98100 11009", "Finance", "Accountant", "68000", "2022-08-22", EmployeeStatus.INACTIVE},
                {"Isha", "Malhotra", "isha.malhotra@example.com", "+91 98100 11010", "Design", "UI/UX Designer", "78000", "2025-01-06", EmployeeStatus.ACTIVE},
                {"Dev", "Joshi", "dev.joshi@example.com", "+91 98100 11011", "Engineering", "Senior Software Engineer", "152000", "2021-05-17", EmployeeStatus.ACTIVE},
                {"Kavya", "Reddy", "kavya.reddy@example.com", "+91 98100 11012", "Human Resources", "HR Executive", "62000", "2025-08-04", EmployeeStatus.ACTIVE},
        };
        for (Object[] r : rows) {
            Employee e = new Employee();
            e.setFirstName((String) r[0]);
            e.setLastName((String) r[1]);
            e.setEmailId((String) r[2]);
            e.setPhone((String) r[3]);
            e.setDepartment(dept.get((String) r[4]));
            e.setRole(role.get((String) r[5]));
            e.setSalary(new BigDecimal((String) r[6]));
            e.setJoiningDate(LocalDate.parse((String) r[7]));
            e.setStatus((EmployeeStatus) r[8]);
            employeeRepository.save(e);
        }
        log.info("Seeded {} departments, {} roles and {} employees", dept.size(), role.size(), rows.length);
    }
}
