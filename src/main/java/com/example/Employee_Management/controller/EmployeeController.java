package com.example.Employee_Management.controller;

import com.example.Employee_Management.dto.AuditEntry;
import com.example.Employee_Management.dto.EmployeeRequest;
import com.example.Employee_Management.dto.EmployeeResponse;
import com.example.Employee_Management.dto.ImportResult;
import com.example.Employee_Management.dto.PageResponse;
import com.example.Employee_Management.exception.BadRequestException;
import com.example.Employee_Management.model.EmployeeStatus;
import com.example.Employee_Management.service.CsvService;
import com.example.Employee_Management.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final EmployeeService employeeService;
    private final CsvService csvService;

    public EmployeeController(EmployeeService employeeService, CsvService csvService) {
        this.employeeService = employeeService;
        this.csvService = csvService;
    }

    /** List employees, with optional search text, filters, paging and sorting. */
    @GetMapping
    public PageResponse<EmployeeResponse> getEmployees(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) EmployeeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        return employeeService.search(q, departmentId, roleId, status, page, size, sort, direction);
    }

    @GetMapping("/{id}")
    public EmployeeResponse getEmployeeById(@PathVariable Long id) {
        return employeeService.findById(id);
    }

    /** Who changed this employee's record, and how. Newest first. */
    @GetMapping("/{id}/history")
    public List<AuditEntry> getEmployeeHistory(@PathVariable Long id) {
        return employeeService.history(id);
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> createEmployee(@Valid @RequestBody EmployeeRequest request) {
        EmployeeResponse created = employeeService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public EmployeeResponse updateEmployee(@PathVariable Long id, @Valid @RequestBody EmployeeRequest request) {
        return employeeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> deleteEmployee(@PathVariable Long id) {
        employeeService.delete(id);
        return Map.of("deleted", Boolean.TRUE);
    }

    // ---------------------------------------------------------------- CSV

    /** Download the employees matching the same filters as the list endpoint, as CSV. */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportCsv(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) EmployeeStatus status,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        String csv = csvService.export(employeeService.searchAll(q, departmentId, roleId, status, sort, direction));
        return csvResponse(csv, "employees-" + LocalDate.now() + ".csv");
    }

    /** An empty CSV with the expected header and one example row. */
    @GetMapping(value = "/import/template", produces = "text/csv")
    public ResponseEntity<String> importTemplate() {
        return csvResponse(csvService.template(), "employees-template.csv");
    }

    /** Add employees from a CSV file (multipart field "file"). Valid rows are added; the rest are reported. */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BadRequestException("The file is empty");
        }
        return csvService.importCsv(new String(file.getBytes(), StandardCharsets.UTF_8));
    }

    private static ResponseEntity<String> csvResponse(String csv, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(CSV)
                .body(csv);
    }
}
