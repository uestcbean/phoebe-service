package com.phoebe.controller;

import com.phoebe.context.UserContext;
import com.phoebe.dto.LoginRequest;
import com.phoebe.dto.LoginResponse;
import com.phoebe.dto.UserRequest;
import com.phoebe.entity.User;
import com.phoebe.service.TokenService;
import com.phoebe.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for user management operations.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;
    private final TokenService tokenService;

    public UserController(UserService userService, TokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    /**
     * User login - returns token for authentication
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            User user = userService.login(request);
            String token = tokenService.generateToken(user.getId());
            LoginResponse response = new LoginResponse(token, user);
            log.info("User logged in: {}", user.getUsername());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Login failed for user {}: {}", request.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * User logout - invalidates the current token
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            tokenService.invalidateToken(token);
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * Get current logged-in user info
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        User user = UserContext.getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in"));
        }
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    /**
     * Create a new user (for admin registration)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<?> createUser(@Valid @RequestBody UserRequest request) {
        try {
            User user = userService.createUser(request);
            // Don't return password in response
            user.setPassword(null);
            return ResponseEntity.status(HttpStatus.CREATED).body(user);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to create user: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get user by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    /**
     * Get user by username
     */
    @GetMapping("/username/{username}")
    public ResponseEntity<?> getUserByUsername(@PathVariable String username) {
        User user = userService.getUserByUsername(username);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    /**
     * Get all users with optional filtering
     */
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        List<User> users;
        if (keyword != null && !keyword.isBlank()) {
            users = userService.searchUsers(keyword);
        } else if (status != null) {
            users = userService.getUsersByStatus(status);
        } else {
            users = userService.getAllUsers();
        }
        return ResponseEntity.ok(users);
    }

    /**
     * Update user
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        try {
            User user = userService.updateUser(id, request);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to update user: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Enable user
     */
    @PostMapping("/{id}/enable")
    public ResponseEntity<?> enableUser(@PathVariable Long id) {
        try {
            User user = userService.enableUser(id);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Disable user
     */
    @PostMapping("/{id}/disable")
    public ResponseEntity<?> disableUser(@PathVariable Long id) {
        try {
            User user = userService.disableUser(id);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete user
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok(Map.of("message", "User deleted successfully", "id", id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get user statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getUserStats() {
        UserService.UserStats stats = userService.getUserStats();
        Map<String, Object> result = new HashMap<>();
        result.put("total", stats.total());
        result.put("active", stats.active());
        result.put("disabled", stats.disabled());
        return ResponseEntity.ok(result);
    }
}
