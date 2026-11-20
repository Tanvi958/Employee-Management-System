package com.example.Employee_Management.controller;

import com.example.Employee_Management.dto.EmployeeRequest;
import com.example.Employee_Management.dto.EmployeeResponse;
import com.example.Employee_Management.dto.PageResponse;
import com.example.Employee_Management.model.EmployeeStatus;
import com.example.Employee_Management.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
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
    public ResponseEntity<Map<String, Boolean>> deleteEmployee(@PathVariable Long id) {
        employeeService.delete(id);
        return ResponseEntity.status(HttpStatus.OK).body(Map.of("deleted", Boolean.TRUE));
    }
}
