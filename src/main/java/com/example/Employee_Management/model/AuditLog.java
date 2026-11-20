package com.example.Employee_Management.model;

import jakarta.persistence.*;

import java.time.Instant;

/** One recorded change: who did what to which record, and when. */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_audit_time", columnList = "occurred_at")
})
public class AuditLog {

    public enum Action { CREATE, UPDATE, DELETE, IMPORT }

    public enum EntityType { EMPLOYEE, DEPARTMENT, ROLE, USER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 50)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private EntityType entityType;

    @Column(name = "entity_id")
    private Long entityId;

    /** Short human-readable description, e.g. "Updated Priya Verma". */
    @Column(nullable = false, length = 255)
    private String summary;

    /** For updates: the fields that changed, e.g. "salary: 90000 → 95000; status: ACTIVE → ON_LEAVE". */
    @Column(length = 2000)
    private String changes;

    protected AuditLog() {
    }

    public AuditLog(String actor, Action action, EntityType entityType, Long entityId, String summary,
                    String changes) {
        this.occurredAt = Instant.now();
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.summary = summary.length() > 255 ? summary.substring(0, 252) + "..." : summary;
        this.changes = changes == null || changes.length() <= 2000 ? changes : changes.substring(0, 1997) + "...";
    }

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getActor() { return actor; }
    public Action getAction() { return action; }
    public EntityType getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getSummary() { return summary; }
    public String getChanges() { return changes; }
}
