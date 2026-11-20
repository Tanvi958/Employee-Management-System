package com.example.Employee_Management.controller;

import com.example.Employee_Management.dto.UserDto;
import com.example.Employee_Management.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final boolean demoAccounts;

    public AuthController(UserService userService,
                          @Value("${app.seed-data:true}") boolean seedData,
                          @Value("${app.admin.username}") String adminUsername,
                          @Value("${app.admin.password}") String adminPassword) {
        this.userService = userService;
        // Show the demo sign-in buttons only while the demo passwords are the real ones.
        this.demoAccounts = seedData && "admin".equals(adminUsername) && "admin123".equals(adminPassword);
    }

    /** Public: whether the sign-in page should offer the demo accounts. */
    @GetMapping("/demo")
    public Map<String, Boolean> demo() {
        return Map.of("enabled", demoAccounts);
    }

    /** Exchange a username and password for a JWT. */
    @PostMapping("/login")
    public UserDto.LoginResponse login(@Valid @RequestBody UserDto.LoginRequest request) {
        return userService.login(request);
    }

    /** The signed-in user. */
    @GetMapping("/me")
    public UserDto.Response me() {
        return userService.me();
    }
}
