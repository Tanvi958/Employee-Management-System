package com.example.Employee_Management.service;

import com.example.Employee_Management.dto.EmployeeRequest;
import com.example.Employee_Management.dto.EmployeeResponse;
import com.example.Employee_Management.dto.ImportResult;
import com.example.Employee_Management.exception.BadRequestException;
import com.example.Employee_Management.model.AuditLog;
import com.example.Employee_Management.model.Department;
import com.example.Employee_Management.model.EmployeeStatus;
import com.example.Employee_Management.model.Role;
import com.example.Employee_Management.repository.DepartmentRepository;
import com.example.Employee_Management.repository.EmployeeRepository;
import com.example.Employee_Management.repository.RoleRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Employee CSV export and import.
 *
 * <p>Columns: {@code firstName,lastName,email,phone,department,role,salary,joiningDate,status}.
 * Department and role are matched by name (case-insensitive) and must already exist. Each valid row is
 * imported; invalid rows are skipped and reported back with their line number and reason.
 */
@Service
public class CsvService {

    public static final List<String> COLUMNS = List.of(
            "firstName", "lastName", "email", "phone", "department", "role", "salary", "joiningDate", "status");
    public static final int MAX_ROWS = 1000;

    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final AuditService audit;
    private final Validator validator;

    public CsvService(EmployeeService employeeService, EmployeeRepository employeeRepository,
                      DepartmentRepository departmentRepository, RoleRepository roleRepository,
                      AuditService audit, Validator validator) {
        this.employeeService = employeeService;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        this.audit = audit;
        this.validator = validator;
    }

    // ---------------------------------------------------------------- export

    public String export(List<EmployeeResponse> employees) {
        StringBuilder out = new StringBuilder(String.join(",", COLUMNS)).append("\r\n");
        for (EmployeeResponse e : employees) {
            out.append(String.join(",", List.of(
                    cell(e.firstName()), cell(e.lastName()), cell(e.emailId()), cell(e.phone()),
                    cell(e.department() == null ? null : e.department().name()),
                    cell(e.role() == null ? null : e.role().name()),
                    cell(e.salary() == null ? null : e.salary().toPlainString()),
                    cell(e.joiningDate() == null ? null : e.joiningDate().toString()),
                    cell(e.status().name())))).append("\r\n");
        }
        return out.toString();
    }

    /** Quotes a value when needed, and neutralises leading =,+,-,@ so spreadsheets don't run it as a formula. */
    private static String cell(String value) {
        if (value == null) return "";
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0 && !v.matches("^[+-]?[0-9][0-9 ()-]*$")) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    // ---------------------------------------------------------------- import

    @Transactional
    public ImportResult importCsv(String content) {
        List<List<String>> rows = parse(content);
        if (rows.isEmpty()) {
            throw new BadRequestException("The file is empty");
        }
        Map<String, Integer> index = headerIndex(rows.getFirst());
        if (rows.size() - 1 > MAX_ROWS) {
            throw new BadRequestException("Too many rows: the limit is " + MAX_ROWS + " per file");
        }

        Map<String, Department> departments = departmentRepository.findAll().stream()
                .collect(Collectors.toMap(d -> d.getName().toLowerCase(), Function.identity()));
        Map<String, Role> roles = roleRepository.findAll().stream()
                .collect(Collectors.toMap(r -> r.getTitle().toLowerCase(), Function.identity()));

        List<ImportResult.RowError> errors = new ArrayList<>();
        Set<String> emailsInFile = new HashSet<>();
        int imported = 0;

        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            int line = i + 1;
            if (row.stream().allMatch(String::isBlank)) continue;   // skip empty lines
            Function<String, String> col = name -> {
                Integer at = index.get(name);
                return at == null || at >= row.size() ? "" : row.get(at).trim();
            };
            try {
                String deptName = col.apply("department");
                String roleName = col.apply("role");
                Department dept = deptName.isEmpty() ? null : departments.get(deptName.toLowerCase());
                Role role = roleName.isEmpty() ? null : roles.get(roleName.toLowerCase());
                if (!deptName.isEmpty() && dept == null) throw new RowException("Unknown department '" + deptName + "'");
                if (!roleName.isEmpty() && role == null) throw new RowException("Unknown role '" + roleName + "'");

                EmployeeRequest request = new EmployeeRequest(
                        col.apply("firstName"),
                        emptyToNull(col.apply("lastName")),
                        col.apply("email"),
                        emptyToNull(col.apply("phone")),
                        dept == null ? null : dept.getId(),
                        role == null ? null : role.getId(),
                        parseSalary(col.apply("salary")),
                        parseDate(col.apply("joiningDate")),
                        parseStatus(col.apply("status")));

                Set<ConstraintViolation<EmployeeRequest>> violations = validator.validate(request);
                if (!violations.isEmpty()) {
                    throw new RowException(violations.stream().map(ConstraintViolation::getMessage).sorted()
                            .collect(Collectors.joining("; ")));
                }
                String email = request.emailId().trim().toLowerCase();
                if (!emailsInFile.add(email)) throw new RowException("Email " + email + " appears twice in the file");
                if (employeeRepository.existsByEmailIdIgnoreCase(email)) {
                    throw new RowException("An employee with email " + email + " already exists");
                }
                employeeService.create(request);
                imported++;
            } catch (RowException ex) {
                errors.add(new ImportResult.RowError(line, ex.getMessage()));
            }
        }
        if (imported > 0) {
            audit.record(AuditLog.Action.IMPORT, AuditLog.EntityType.EMPLOYEE, null,
                    "Imported " + imported + " employee(s) from CSV"
                            + (errors.isEmpty() ? "" : ", skipped " + errors.size()));
        }
        return new ImportResult(imported, errors.size(), errors);
    }

    /** A CSV template with the header and one example row. */
    public String template() {
        return String.join(",", COLUMNS) + "\r\n"
                + "Riya,Sharma,riya.sharma@example.com,+91 98100 22001,Engineering,Software Engineer,85000,2026-01-15,ACTIVE\r\n";
    }

    private static Map<String, Integer> headerIndex(List<String> header) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i).trim().replace("﻿", "");
            for (String c : COLUMNS) {
                if (c.equalsIgnoreCase(h) || ("emailId".equalsIgnoreCase(h) && c.equals("email"))) {
                    index.put(c, i);
                }
            }
        }
        for (String required : List.of("firstName", "email")) {
            if (!index.containsKey(required)) {
                throw new BadRequestException("Missing column '" + required + "'. Expected header: "
                        + String.join(",", COLUMNS));
            }
        }
        return index;
    }

    private static BigDecimal parseSalary(String value) {
        if (value.isEmpty()) return null;
        try {
            return new BigDecimal(value.replace(",", "").replace("₹", "").trim());
        } catch (NumberFormatException e) {
            throw new RowException("Salary '" + value + "' is not a number");
        }
    }

    private static LocalDate parseDate(String value) {
        if (value.isEmpty()) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new RowException("Joining date '" + value + "' must look like 2026-01-31");
        }
    }

    private static EmployeeStatus parseStatus(String value) {
        if (value.isEmpty()) return EmployeeStatus.ACTIVE;
        try {
            return EmployeeStatus.valueOf(value.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            throw new RowException("Status '" + value + "' must be ACTIVE, ON_LEAVE or INACTIVE");
        }
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    /** Minimal RFC 4180 parser: commas, double-quoted fields, "" escapes, CRLF or LF line ends. */
    static List<List<String>> parse(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') i++;
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private static final class RowException extends RuntimeException {
        RowException(String message) {
            super(message);
        }
    }
}
