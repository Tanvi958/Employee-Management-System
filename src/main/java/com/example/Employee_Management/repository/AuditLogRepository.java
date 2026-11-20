package com.example.Employee_Management.repository;

import com.example.Employee_Management.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByEntityType(AuditLog.EntityType entityType, Pageable pageable);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc(AuditLog.EntityType entityType, Long entityId);

    List<AuditLog> findTop8ByOrderByOccurredAtDescIdDesc();
}
