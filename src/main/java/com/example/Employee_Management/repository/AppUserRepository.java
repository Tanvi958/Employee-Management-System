package com.example.Employee_Management.repository;

import com.example.Employee_Management.model.AppUser;
import com.example.Employee_Management.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    long countByRole(UserRole role);
}
