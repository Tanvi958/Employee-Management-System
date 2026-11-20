package com.example.Employee_Management.controller;

import com.example.Employee_Management.dto.AuditEntry;
import com.example.Employee_Management.dto.PageResponse;
import com.example.Employee_Management.model.AuditLog;
import com.example.Employee_Management.service.AuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The full audit log. Admin only (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public PageResponse<AuditEntry> getAuditLog(@RequestParam(required = false) AuditLog.EntityType entityType,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return auditService.list(entityType, page, size);
    }
}
