package com.example.Employee_Management.controller;

import com.example.Employee_Management.dto.RoleDto;
import com.example.Employee_Management.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public List<RoleDto.Response> getRoles() {
        return roleService.findAll();
    }

    @GetMapping("/{id}")
    public RoleDto.Response getRole(@PathVariable Long id) {
        return roleService.findById(id);
    }

    @PostMapping
    public ResponseEntity<RoleDto.Response> createRole(@Valid @RequestBody RoleDto.Request request) {
        RoleDto.Response created = roleService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public RoleDto.Response updateRole(@PathVariable Long id, @Valid @RequestBody RoleDto.Request request) {
        return roleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> deleteRole(@PathVariable Long id) {
        roleService.delete(id);
        return Map.of("deleted", Boolean.TRUE);
    }
}
