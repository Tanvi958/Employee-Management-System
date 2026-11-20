package com.example.Employee_Management.service;

import com.example.Employee_Management.dto.DepartmentDto;
import com.example.Employee_Management.exception.ConflictException;
import com.example.Employee_Management.exception.ResourceNotFoundException;
import com.example.Employee_Management.model.AuditLog;
import com.example.Employee_Management.model.Department;
import com.example.Employee_Management.repository.DepartmentRepository;
import com.example.Employee_Management.repository.EmployeeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService audit;

    public DepartmentService(DepartmentRepository departmentRepository, EmployeeRepository employeeRepository, AuditService audit) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.audit = audit;
    }

    public List<DepartmentDto.Response> findAll() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : employeeRepository.countPerDepartment()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return departmentRepository.findAll(Sort.by("name")).stream()
                .map(d -> toResponse(d, counts.getOrDefault(d.getId(), 0L)))
                .toList();
    }

    public DepartmentDto.Response findById(Long id) {
        Department d = getOrThrow(id);
        return toResponse(d, employeeRepository.countByDepartmentId(id));
    }

    @Transactional
    public DepartmentDto.Response create(DepartmentDto.Request request) {
        String name = request.name().trim();
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Department '" + name + "' already exists");
        }
        Department saved = departmentRepository.save(new Department(name, blankToNull(request.description())));
        audit.record(AuditLog.Action.CREATE, AuditLog.EntityType.DEPARTMENT, saved.getId(),
                "Added department " + saved.getName());
        return toResponse(saved, 0);
    }

    @Transactional
    public DepartmentDto.Response update(Long id, DepartmentDto.Request request) {
        Department d = getOrThrow(id);
        String name = request.name().trim();
        if (departmentRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("Department '" + name + "' already exists");
        }
        AuditService.Diff diff = new AuditService.Diff()
                .field("name", d.getName(), name)
                .field("description", d.getDescription(), blankToNull(request.description()));
        d.setName(name);
        d.setDescription(blankToNull(request.description()));
        if (!diff.isEmpty()) {
            audit.record(AuditLog.Action.UPDATE, AuditLog.EntityType.DEPARTMENT, id, "Updated department " + name, diff.toString());
        }
        return toResponse(departmentRepository.save(d), employeeRepository.countByDepartmentId(id));
    }

    /** A department can only be deleted once no employee belongs to it. */
    @Transactional
    public void delete(Long id) {
        Department d = getOrThrow(id);
        long inUse = employeeRepository.countByDepartmentId(id);
        if (inUse > 0) {
            throw new ConflictException("Cannot delete department '" + d.getName() + "': "
                    + inUse + " employee(s) still belong to it");
        }
        departmentRepository.delete(d);
        audit.record(AuditLog.Action.DELETE, AuditLog.EntityType.DEPARTMENT, id, "Deleted department " + d.getName());
    }

    private Department getOrThrow(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));
    }

    private static DepartmentDto.Response toResponse(Department d, long count) {
        return new DepartmentDto.Response(d.getId(), d.getName(), d.getDescription(), count);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
