package com.example.Employee_Management.controller;

import com.example.Employee_Management.dto.DepartmentDto;
import com.example.Employee_Management.service.DepartmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public List<DepartmentDto.Response> getDepartments() {
        return departmentService.findAll();
    }

    @GetMapping("/{id}")
    public DepartmentDto.Response getDepartment(@PathVariable Long id) {
        return departmentService.findById(id);
    }

    @PostMapping
    public ResponseEntity<DepartmentDto.Response> createDepartment(@Valid @RequestBody DepartmentDto.Request request) {
        DepartmentDto.Response created = departmentService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public DepartmentDto.Response updateDepartment(@PathVariable Long id,
                                                   @Valid @RequestBody DepartmentDto.Request request) {
        return departmentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> deleteDepartment(@PathVariable Long id) {
        departmentService.delete(id);
        return Map.of("deleted", Boolean.TRUE);
    }
}
