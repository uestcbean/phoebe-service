package com.phoebe.mapper;

import com.phoebe.entity.BailianIndexPool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Mapper for Bailian Index Pool operations.
 * Manages the pool of available knowledge base indexes for user assignment.
 */
@Mapper
public interface BailianIndexPoolMapper {

    /**
     * Insert a new index into the pool
     */
    int insert(BailianIndexPool indexPool);

    /**
     * Update an existing index pool record
     */
    int update(BailianIndexPool indexPool);

    /**
     * Find by ID
     */
    BailianIndexPool findById(@Param("id") Long id);

    /**
     * Find by index ID
     */
    BailianIndexPool findByIndexId(@Param("indexId") String indexId);

    /**
     * Find by assigned user ID
     */
    BailianIndexPool findByAssignedUserId(@Param("userId") Long userId);

    /**
     * Find first available index (for assignment)
     */
    BailianIndexPool findFirstAvailable();

    /**
     * Find all indexes by status
     */
    List<BailianIndexPool> findByStatus(@Param("status") String status);

    /**
     * Find all indexes
     */
    List<BailianIndexPool> findAll();

    /**
     * Assign an index to a user (atomic operation)
     * Returns number of rows affected (1 if successful, 0 if index was already assigned)
     */
    int assignToUser(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Release an index (make it available again)
     */
    int release(@Param("id") Long id);

    /**
     * Disable an index
     */
    int disable(@Param("id") Long id);

    /**
     * Enable an index (make it available if not assigned)
     */
    int enable(@Param("id") Long id);

    /**
     * Delete by ID
     */
    int deleteById(@Param("id") Long id);

    /**
     * Count by status
     */
    long countByStatus(@Param("status") String status);

    /**
     * Count all indexes
     */
    long countAll();
}
