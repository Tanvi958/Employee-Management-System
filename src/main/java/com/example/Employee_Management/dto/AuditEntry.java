package com.example.Employee_Management.dto;

import com.example.Employee_Management.model.AuditLog;

import java.time.Instant;

public record AuditEntry(
        Long id,
        Instant occurredAt,
        String actor,
        AuditLog.Action action,
        AuditLog.EntityType entityType,
        Long entityId,
        String summary,
        String changes
) {
    public static AuditEntry from(AuditLog a) {
        return new AuditEntry(a.getId(), a.getOccurredAt(), a.getActor(), a.getAction(), a.getEntityType(),
                a.getEntityId(), a.getSummary(), a.getChanges());
    }
}
