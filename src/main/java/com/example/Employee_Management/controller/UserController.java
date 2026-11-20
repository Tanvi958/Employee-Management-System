package com.example.Employee_Management.controller;

import com.example.Employee_Management.dto.UserDto;
import com.example.Employee_Management.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** User accounts. Admin only (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserDto.Response> getUsers() {
        return userService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto.Response createUser(@Valid @RequestBody UserDto.CreateRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public UserDto.Response updateUser(@PathVariable Long id, @Valid @RequestBody UserDto.UpdateRequest request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return Map.of("deleted", Boolean.TRUE);
    }
}
