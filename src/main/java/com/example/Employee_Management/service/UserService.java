package com.example.Employee_Management.service;

import com.example.Employee_Management.dto.UserDto;
import com.example.Employee_Management.exception.ConflictException;
import com.example.Employee_Management.exception.ResourceNotFoundException;
import com.example.Employee_Management.model.AppUser;
import com.example.Employee_Management.model.AuditLog;
import com.example.Employee_Management.model.UserRole;
import com.example.Employee_Management.repository.AppUserRepository;
import com.example.Employee_Management.security.CurrentUser;
import com.example.Employee_Management.security.TokenService;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final AuditService audit;

    public UserService(AppUserRepository users, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, TokenService tokenService, AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.audit = audit;
    }

    /** Checks the password and returns a token. Throws BadCredentialsException (→ 401) on failure. */
    @Transactional
    public UserDto.LoginResponse login(UserDto.LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username().trim(), request.password()));
        AppUser user = users.findByUsernameIgnoreCase(request.username().trim()).orElseThrow();
        user.setLastLoginAt(Instant.now());
        TokenService.IssuedToken token = tokenService.issue(user);
        return new UserDto.LoginResponse(token.token(), token.expiresAt(), UserDto.Response.from(user));
    }

    public UserDto.Response me() {
        return users.findByUsernameIgnoreCase(CurrentUser.username())
                .map(UserDto.Response::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public List<UserDto.Response> findAll() {
        return users.findAll(Sort.by("username")).stream().map(UserDto.Response::from).toList();
    }

    @Transactional
    public UserDto.Response create(UserDto.CreateRequest request) {
        String username = request.username().trim().toLowerCase();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("Username '" + username + "' is already taken");
        }
        AppUser saved = users.save(new AppUser(username, request.fullName().trim(),
                passwordEncoder.encode(request.password()), request.role()));
        audit.record(AuditLog.Action.CREATE, AuditLog.EntityType.USER, saved.getId(),
                "Added user " + username + " (" + saved.getRole() + ")");
        return UserDto.Response.from(saved);
    }

    @Transactional
    public UserDto.Response update(Long id, UserDto.UpdateRequest request) {
        AppUser user = getOrThrow(id);
        if (user.getRole() == UserRole.ADMIN && request.role() != UserRole.ADMIN
                && users.countByRole(UserRole.ADMIN) == 1) {
            throw new ConflictException("There must be at least one admin");
        }
        boolean newPassword = request.password() != null && !request.password().isBlank();
        AuditService.Diff diff = new AuditService.Diff()
                .field("name", user.getFullName(), request.fullName().trim())
                .field("role", user.getRole(), request.role());
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        if (newPassword) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        String changes = newPassword
                ? (diff.isEmpty() ? "password changed" : diff + "; password changed")
                : diff.toString();
        audit.record(AuditLog.Action.UPDATE, AuditLog.EntityType.USER, id, "Updated user " + user.getUsername(), changes);
        return UserDto.Response.from(user);
    }

    @Transactional
    public void delete(Long id) {
        AppUser user = getOrThrow(id);
        if (user.getUsername().equalsIgnoreCase(CurrentUser.username())) {
            throw new ConflictException("You can't delete your own account");
        }
        if (user.getRole() == UserRole.ADMIN && users.countByRole(UserRole.ADMIN) == 1) {
            throw new ConflictException("There must be at least one admin");
        }
        users.delete(user);
        audit.record(AuditLog.Action.DELETE, AuditLog.EntityType.USER, id, "Deleted user " + user.getUsername());
    }

    private AppUser getOrThrow(Long id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }
}
