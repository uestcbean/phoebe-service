package com.phoebe.service;

import com.phoebe.dto.LoginRequest;
import com.phoebe.dto.UserRequest;
import com.phoebe.entity.BailianIndexPool;
import com.phoebe.entity.User;
import com.phoebe.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserMapper userMapper;
    private final BailianIndexPoolService indexPoolService;

    public UserService(UserMapper userMapper, @Lazy BailianIndexPoolService indexPoolService) {
        this.userMapper = userMapper;
        this.indexPoolService = indexPoolService;
    }

    /**
     * Create a new user.
     * This will also assign a dedicated Bailian knowledge base index to the user.
     */
    @Transactional
    public User createUser(UserRequest request) {
        // Password is required for new users
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required for new users");
        }
        if (request.getPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        
        // Check if username already exists
        if (userMapper.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + request.getUsername());
        }

        // Check if email already exists (if provided)
        if (request.getEmail() != null && !request.getEmail().isBlank() 
            && userMapper.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        User user = new User(
                request.getUsername(),
                request.getPassword(),
                request.getEmail(),
                request.getPhone(),
                request.getNickname() != null ? request.getNickname() : request.getUsername(),
                request.getAvatarUrl(),
                User.STATUS_ACTIVE
        );

        userMapper.insert(user);
        log.info("Created user: id={}, username={}", user.getId(), request.getUsername());

        // Assign a dedicated Bailian knowledge base index to the user
        try {
            BailianIndexPool assignedIndex = indexPoolService.assignIndexToUser(user.getId());
            log.info("Assigned Bailian index {} to user {}", assignedIndex.getIndexId(), user.getId());
        } catch (Exception e) {
            log.error("Failed to assign Bailian index to user {}: {}", user.getId(), e.getMessage());
            // Don't fail user creation if index assignment fails
            // The admin can manually assign later
        }

        return user;
    }

    /**
     * Update an existing user
     */
    @Transactional
    public User updateUser(Long id, UserRequest request) {
        User existingUser = userMapper.findById(id);
        if (existingUser == null) {
            throw new IllegalArgumentException("User not found: " + id);
        }

        // Check if new username conflicts with another user
        if (!existingUser.getUsername().equals(request.getUsername())) {
            User conflictUser = userMapper.findByUsername(request.getUsername());
            if (conflictUser != null && !conflictUser.getId().equals(id)) {
                throw new IllegalArgumentException("Username already exists: " + request.getUsername());
            }
        }

        // Check if new email conflicts with another user
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            User conflictUser = userMapper.findByEmail(request.getEmail());
            if (conflictUser != null && !conflictUser.getId().equals(id)) {
                throw new IllegalArgumentException("Email already exists: " + request.getEmail());
            }
        }

        existingUser.setUsername(request.getUsername());
        existingUser.setEmail(request.getEmail());
        existingUser.setPhone(request.getPhone());
        existingUser.setNickname(request.getNickname());
        existingUser.setAvatarUrl(request.getAvatarUrl());
        // Only update password if provided (not null and not empty)
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            existingUser.setPassword(request.getPassword());
        }
        existingUser.setUpdatedAt(LocalDateTime.now());

        userMapper.update(existingUser);
        log.info("Updated user: id={}, username={}", id, request.getUsername());
        return existingUser;
    }

    /**
     * Login user with username and password
     */
    @Transactional
    public User login(LoginRequest request) {
        User user = userMapper.findByUsernameAndPassword(request.getUsername(), request.getPassword());
        if (user == null) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (user.getStatus() == User.STATUS_DISABLED) {
            throw new IllegalArgumentException("账户已被禁用");
        }
        // Update last login time
        userMapper.updateLastLoginAt(user.getId());
        user.setLastLoginAt(LocalDateTime.now());
        log.info("User logged in: id={}, username={}", user.getId(), user.getUsername());
        return user;
    }

    /**
     * Get user by ID
     */
    public User getUserById(Long id) {
        return userMapper.findById(id);
    }

    /**
     * Get user by username
     */
    public User getUserByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    /**
     * Get all users
     */
    public List<User> getAllUsers() {
        return userMapper.findAll();
    }

    /**
     * Get users by status
     */
    public List<User> getUsersByStatus(Integer status) {
        return userMapper.findByStatus(status);
    }

    /**
     * Search users by keyword
     */
    public List<User> searchUsers(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return userMapper.findAll();
        }
        return userMapper.searchByKeyword(keyword);
    }

    /**
     * Enable user
     */
    @Transactional
    public User enableUser(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + id);
        }
        user.setStatus(User.STATUS_ACTIVE);
        userMapper.update(user);
        log.info("Enabled user: id={}", id);
        return user;
    }

    /**
     * Disable user
     */
    @Transactional
    public User disableUser(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + id);
        }
        user.setStatus(User.STATUS_DISABLED);
        userMapper.update(user);
        log.info("Disabled user: id={}", id);
        return user;
    }

    /**
     * Delete user.
     * This will also release the assigned Bailian knowledge base index.
     */
    @Transactional
    public void deleteUser(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + id);
        }

        // Release the assigned Bailian index
        try {
            indexPoolService.releaseIndex(id);
            log.info("Released Bailian index for user {}", id);
        } catch (Exception e) {
            log.warn("Failed to release Bailian index for user {}: {}", id, e.getMessage());
        }

        userMapper.deleteById(id);
        log.info("Deleted user: id={}", id);
    }

    /**
     * Update last login time
     */
    @Transactional
    public void updateLastLogin(Long id) {
        userMapper.updateLastLoginAt(id);
    }

    /**
     * Get user statistics
     */
    public UserStats getUserStats() {
        long total = userMapper.countAll();
        long active = userMapper.countByStatus(User.STATUS_ACTIVE);
        long disabled = userMapper.countByStatus(User.STATUS_DISABLED);
        return new UserStats(total, active, disabled);
    }

    /**
     * User statistics record
     */
    public record UserStats(long total, long active, long disabled) {}
}
