package com.example.Employee_Management.service;

import com.example.Employee_Management.dto.AuditEntry;
import com.example.Employee_Management.dto.PageResponse;
import com.example.Employee_Management.model.AuditLog;
import com.example.Employee_Management.repository.AuditLogRepository;
import com.example.Employee_Management.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Records every create, update, delete and import. Entries are written in the same transaction as the
 * change itself, so a rolled-back change leaves no audit entry behind.
 */
@Service
@Transactional(readOnly = true)
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(AuditLog.Action action, AuditLog.EntityType type, Long entityId, String summary) {
        record(action, type, entityId, summary, null);
    }

    @Transactional
    public void record(AuditLog.Action action, AuditLog.EntityType type, Long entityId, String summary,
                       String changes) {
        repository.save(new AuditLog(CurrentUser.username(), action, type, entityId, summary, changes));
    }

    public PageResponse<AuditEntry> list(AuditLog.EntityType type, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
        var result = type == null ? repository.findAll(pageable) : repository.findByEntityType(type, pageable);
        return PageResponse.from(result.map(AuditEntry::from));
    }

    public List<AuditEntry> history(AuditLog.EntityType type, Long entityId) {
        return repository.findByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc(type, entityId).stream()
                .map(AuditEntry::from).toList();
    }

    public List<AuditEntry> recent() {
        return repository.findTop8ByOrderByOccurredAtDescIdDesc().stream().map(AuditEntry::from).toList();
    }

    /** Collects "field: old → new" pairs for the fields that actually changed. */
    public static final class Diff {
        private final List<String> parts = new ArrayList<>();

        public Diff field(String name, Object before, Object after) {
            String b = display(before);
            String a = display(after);
            if (!Objects.equals(b, a)) {
                parts.add(name + ": " + b + " → " + a);
            }
            return this;
        }

        public boolean isEmpty() {
            return parts.isEmpty();
        }

        @Override
        public String toString() {
            return parts.isEmpty() ? null : String.join("; ", parts);
        }

        private static String display(Object value) {
            if (value == null) return "—";
            if (value instanceof java.math.BigDecimal bd) return bd.stripTrailingZeros().toPlainString();
            String s = value.toString();
            return s.isBlank() ? "—" : s;
        }
    }
}
