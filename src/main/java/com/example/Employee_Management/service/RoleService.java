package com.example.Employee_Management.service;

import com.example.Employee_Management.dto.RoleDto;
import com.example.Employee_Management.exception.ConflictException;
import com.example.Employee_Management.exception.ResourceNotFoundException;
import com.example.Employee_Management.model.AuditLog;
import com.example.Employee_Management.model.Role;
import com.example.Employee_Management.repository.EmployeeRepository;
import com.example.Employee_Management.repository.RoleRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roleRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService audit;

    public RoleService(RoleRepository roleRepository, EmployeeRepository employeeRepository, AuditService audit) {
        this.roleRepository = roleRepository;
        this.employeeRepository = employeeRepository;
        this.audit = audit;
    }

    public List<RoleDto.Response> findAll() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : employeeRepository.countPerRole()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return roleRepository.findAll(Sort.by("title")).stream()
                .map(r -> toResponse(r, counts.getOrDefault(r.getId(), 0L)))
                .toList();
    }

    public RoleDto.Response findById(Long id) {
        Role r = getOrThrow(id);
        return toResponse(r, employeeRepository.countByRoleId(id));
    }

    @Transactional
    public RoleDto.Response create(RoleDto.Request request) {
        String title = request.title().trim();
        if (roleRepository.existsByTitleIgnoreCase(title)) {
            throw new ConflictException("Role '" + title + "' already exists");
        }
        Role saved = roleRepository.save(new Role(title, blankToNull(request.description())));
        audit.record(AuditLog.Action.CREATE, AuditLog.EntityType.ROLE, saved.getId(),
                "Added role " + saved.getTitle());
        return toResponse(saved, 0);
    }

    @Transactional
    public RoleDto.Response update(Long id, RoleDto.Request request) {
        Role r = getOrThrow(id);
        String title = request.title().trim();
        if (roleRepository.existsByTitleIgnoreCaseAndIdNot(title, id)) {
            throw new ConflictException("Role '" + title + "' already exists");
        }
        AuditService.Diff diff = new AuditService.Diff()
                .field("title", r.getTitle(), title)
                .field("description", r.getDescription(), blankToNull(request.description()));
        r.setTitle(title);
        r.setDescription(blankToNull(request.description()));
        if (!diff.isEmpty()) {
            audit.record(AuditLog.Action.UPDATE, AuditLog.EntityType.ROLE, id, "Updated role " + title, diff.toString());
        }
        return toResponse(roleRepository.save(r), employeeRepository.countByRoleId(id));
    }

    /** A role can only be deleted once no employee holds it. */
    @Transactional
    public void delete(Long id) {
        Role r = getOrThrow(id);
        long inUse = employeeRepository.countByRoleId(id);
        if (inUse > 0) {
            throw new ConflictException("Cannot delete role '" + r.getTitle() + "': "
                    + inUse + " employee(s) still hold it");
        }
        roleRepository.delete(r);
        audit.record(AuditLog.Action.DELETE, AuditLog.EntityType.ROLE, id, "Deleted role " + r.getTitle());
    }

    private Role getOrThrow(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + id));
    }

    private static RoleDto.Response toResponse(Role r, long count) {
        return new RoleDto.Response(r.getId(), r.getTitle(), r.getDescription(), count);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
