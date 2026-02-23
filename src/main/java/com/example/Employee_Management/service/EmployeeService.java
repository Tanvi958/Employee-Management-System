package com.example.Employee_Management.service;

import com.example.Employee_Management.exception.ResourceNotFoundException;
import com.example.Employee_Management.model.Employee;
import com.example.Employee_Management.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EmployeeService {

    @Autowired
    private EmployeeRepository employeeRepository;

    // This defines the "findAll" method your controller is looking for
    public List<Employee> findAll() {
        return employeeRepository.findAll();
    }

    // This defines the "save" method
    public Employee save(Employee employee) {
        return employeeRepository.save(employee);
    }

    // This defines the "findById" method
    public Optional<Employee> findById(Long id) {
        return employeeRepository.findById(id);
    }

    // Add this for Update/Delete later
    public void delete(Employee employee) {
        employeeRepository.delete(employee);
    }


    public Employee updateEmployee(Long id, Employee details) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));

        employee.setFirstName(details.getFirstName());
        employee.setLastName(details.getLastName());
        employee.setEmailId(details.getEmailId());
        employee.setDepartment(details.getDepartment());
        employee.setRole(details.getRole());

        return employeeRepository.save(employee);
    }

    public void deleteEmployee(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
        employeeRepository.delete(employee);
    }
}