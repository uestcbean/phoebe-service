package com.phoebe.service;

import com.phoebe.config.BailianConfig;
import com.phoebe.entity.BailianIndexPool;
import com.phoebe.entity.UserKnowledgeBase;
import com.phoebe.mapper.BailianIndexPoolMapper;
import com.phoebe.mapper.UserKnowledgeBaseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing Bailian knowledge base index pool.
 * Handles assignment of indexes to users during registration.
 */
@Service
public class BailianIndexPoolService {

    private static final Logger log = LoggerFactory.getLogger(BailianIndexPoolService.class);

    private final BailianIndexPoolMapper indexPoolMapper;
    private final UserKnowledgeBaseMapper userKnowledgeBaseMapper;
    private final BailianConfig bailianConfig;

    public BailianIndexPoolService(
            BailianIndexPoolMapper indexPoolMapper,
            UserKnowledgeBaseMapper userKnowledgeBaseMapper,
            BailianConfig bailianConfig) {
        this.indexPoolMapper = indexPoolMapper;
        this.userKnowledgeBaseMapper = userKnowledgeBaseMapper;
        this.bailianConfig = bailianConfig;
    }

    /**
     * Add a new index to the pool.
     */
    @Transactional
    public BailianIndexPool addIndexToPool(String indexId, String categoryId, String indexName) {
        // Check if index already exists
        BailianIndexPool existing = indexPoolMapper.findByIndexId(indexId);
        if (existing != null) {
            throw new IllegalArgumentException("Index already exists in pool: " + indexId);
        }

        BailianIndexPool pool = new BailianIndexPool(
                indexId,
                categoryId,
                indexName
        );
        indexPoolMapper.insert(pool);
        log.info("Added index to pool: id={}, indexId={}, categoryId={}", pool.getId(), indexId, categoryId);
        return pool;
    }

    /**
     * Batch add indexes to the pool.
     */
    @Transactional
    public int batchAddIndexes(List<IndexPoolEntry> entries) {
        int added = 0;
        for (IndexPoolEntry entry : entries) {
            try {
                addIndexToPool(entry.indexId(), entry.categoryId(), entry.indexName());
                added++;
            } catch (IllegalArgumentException e) {
                log.warn("Skipping duplicate index: {}", entry.indexId());
            }
        }
        log.info("Batch added {} indexes to pool", added);
        return added;
    }

    /**
     * Assign an index to a user.
     * This is called during user registration.
     *
     * @param userId The user ID to assign an index to
     * @return The assigned index, or null if no available index
     */
    @Transactional
    public BailianIndexPool assignIndexToUser(Long userId) {
        // Check if user already has an assigned index
        BailianIndexPool existing = indexPoolMapper.findByAssignedUserId(userId);
        if (existing != null) {
            log.info("User {} already has assigned index: {}", userId, existing.getIndexId());
            return existing;
        }

        // Find first available index
        BailianIndexPool available = indexPoolMapper.findFirstAvailable();
        if (available == null) {
            log.error("No available index in pool for user {}", userId);
            throw new RuntimeException("No available knowledge base index. Please contact administrator to add more indexes.");
        }

        // Atomic assignment
        int affected = indexPoolMapper.assignToUser(available.getId(), userId);
        if (affected == 0) {
            // Race condition - another thread assigned this index
            // Try again (recursive, but should rarely happen)
            log.warn("Index {} was assigned by another process, retrying...", available.getIndexId());
            return assignIndexToUser(userId);
        }

        // Create UserKnowledgeBase record
        UserKnowledgeBase userKb = new UserKnowledgeBase(
                userId,
                available.getIndexId(),
                bailianConfig.getWorkspaceId(),
                available.getIndexName() != null ? available.getIndexName() : "kb_" + userId,
                UserKnowledgeBase.STATUS_ACTIVE
        );
        userKnowledgeBaseMapper.insert(userKb);

        log.info("Assigned index {} to user {}", available.getIndexId(), userId);
        
        // Refresh and return
        return indexPoolMapper.findById(available.getId());
    }

    /**
     * Get the index assigned to a user.
     */
    public BailianIndexPool getAssignedIndex(Long userId) {
        return indexPoolMapper.findByAssignedUserId(userId);
    }

    /**
     * Get the category ID for a user's assigned index.
     * Falls back to default category ID if no assigned index.
     */
    public String getCategoryIdForUser(Long userId) {
        BailianIndexPool assigned = indexPoolMapper.findByAssignedUserId(userId);
        if (assigned != null) {
            return assigned.getCategoryId();
        }
        // Fallback to default
        return bailianConfig.getDefaultCategoryId();
    }

    /**
     * Release an index (make it available again).
     * This should be called when a user is deleted.
     */
    @Transactional
    public void releaseIndex(Long userId) {
        BailianIndexPool assigned = indexPoolMapper.findByAssignedUserId(userId);
        if (assigned != null) {
            indexPoolMapper.release(assigned.getId());
            log.info("Released index {} from user {}", assigned.getIndexId(), userId);
        }
    }

    /**
     * Get all indexes in the pool.
     */
    public List<BailianIndexPool> getAllIndexes() {
        return indexPoolMapper.findAll();
    }

    /**
     * Get available indexes.
     */
    public List<BailianIndexPool> getAvailableIndexes() {
        return indexPoolMapper.findByStatus(BailianIndexPool.STATUS_AVAILABLE);
    }

    /**
     * Get assigned indexes.
     */
    public List<BailianIndexPool> getAssignedIndexes() {
        return indexPoolMapper.findByStatus(BailianIndexPool.STATUS_ASSIGNED);
    }

    /**
     * Get pool statistics.
     */
    public PoolStats getPoolStats() {
        long total = indexPoolMapper.countAll();
        long available = indexPoolMapper.countByStatus(BailianIndexPool.STATUS_AVAILABLE);
        long assigned = indexPoolMapper.countByStatus(BailianIndexPool.STATUS_ASSIGNED);
        long disabled = indexPoolMapper.countByStatus(BailianIndexPool.STATUS_DISABLED);
        return new PoolStats(total, available, assigned, disabled);
    }

    /**
     * Disable an index.
     */
    @Transactional
    public void disableIndex(Long id) {
        indexPoolMapper.disable(id);
        log.info("Disabled index pool entry: {}", id);
    }

    /**
     * Enable an index.
     */
    @Transactional
    public void enableIndex(Long id) {
        indexPoolMapper.enable(id);
        log.info("Enabled index pool entry: {}", id);
    }

    /**
     * Delete an index from pool (only if not assigned).
     */
    @Transactional
    public void deleteIndex(Long id) {
        BailianIndexPool index = indexPoolMapper.findById(id);
        if (index == null) {
            throw new IllegalArgumentException("Index not found: " + id);
        }
        if (BailianIndexPool.STATUS_ASSIGNED.equals(index.getStatus())) {
            throw new IllegalArgumentException("Cannot delete assigned index. Release it first.");
        }
        indexPoolMapper.deleteById(id);
        log.info("Deleted index from pool: {}", id);
    }

    /**
     * Record for index pool entry.
     */
    public record IndexPoolEntry(String indexId, String categoryId, String indexName) {}

    /**
     * Record for pool statistics.
     */
    public record PoolStats(long total, long available, long assigned, long disabled) {}
}
