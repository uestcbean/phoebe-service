package com.phoebe.mapper;

import com.phoebe.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    /**
     * Insert a new user
     */
    int insert(User user);

    /**
     * Update an existing user
     */
    int update(User user);

    /**
     * Find user by ID
     */
    User findById(@Param("id") Long id);

    /**
     * Find user by username
     */
    User findByUsername(@Param("username") String username);

    /**
     * Find user by email
     */
    User findByEmail(@Param("email") String email);

    /**
     * Find user by phone
     */
    User findByPhone(@Param("phone") String phone);

    /**
     * Find all users
     */
    List<User> findAll();

    /**
     * Find users by status
     */
    List<User> findByStatus(@Param("status") Integer status);

    /**
     * Search users by keyword (username, email, nickname)
     */
    List<User> searchByKeyword(@Param("keyword") String keyword);

    /**
     * Count all users
     */
    long countAll();

    /**
     * Count users by status
     */
    long countByStatus(@Param("status") Integer status);

    /**
     * Update last login time
     */
    int updateLastLoginAt(@Param("id") Long id);

    /**
     * Delete user by ID
     */
    int deleteById(@Param("id") Long id);

    /**
     * Check if username exists
     */
    default boolean existsByUsername(String username) {
        return findByUsername(username) != null;
    }

    /**
     * Check if email exists
     */
    default boolean existsByEmail(String email) {
        return email != null && findByEmail(email) != null;
    }
}
