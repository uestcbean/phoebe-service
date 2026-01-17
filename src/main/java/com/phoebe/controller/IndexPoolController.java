package com.phoebe.controller;

import com.phoebe.entity.BailianIndexPool;
import com.phoebe.service.BailianIndexPoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing Bailian knowledge base index pool.
 * Allows administrators to add, remove, and view indexes in the pool.
 */
@RestController
@RequestMapping("/api/admin/index-pool")
public class IndexPoolController {

    private static final Logger log = LoggerFactory.getLogger(IndexPoolController.class);

    private final BailianIndexPoolService indexPoolService;

    public IndexPoolController(BailianIndexPoolService indexPoolService) {
        this.indexPoolService = indexPoolService;
    }

    /**
     * Add a new index to the pool.
     */
    @PostMapping
    public ResponseEntity<BailianIndexPool> addIndex(@RequestBody AddIndexRequest request) {
        log.info("Adding index to pool: indexId={}, categoryId={}", request.indexId(), request.categoryId());
        try {
            BailianIndexPool pool = indexPoolService.addIndexToPool(
                    request.indexId(),
                    request.categoryId(),
                    request.indexName()
            );
            return ResponseEntity.ok(pool);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Batch add indexes to the pool.
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchAddIndexes(@RequestBody List<AddIndexRequest> requests) {
        log.info("Batch adding {} indexes to pool", requests.size());
        
        List<BailianIndexPoolService.IndexPoolEntry> entries = requests.stream()
                .map(r -> new BailianIndexPoolService.IndexPoolEntry(r.indexId(), r.categoryId(), r.indexName()))
                .toList();
        
        int added = indexPoolService.batchAddIndexes(entries);
        
        Map<String, Object> result = new HashMap<>();
        result.put("requested", requests.size());
        result.put("added", added);
        result.put("message", "Batch add completed");
        
        return ResponseEntity.ok(result);
    }

    /**
     * Get all indexes in the pool.
     */
    @GetMapping
    public ResponseEntity<List<BailianIndexPool>> getAllIndexes() {
        return ResponseEntity.ok(indexPoolService.getAllIndexes());
    }

    /**
     * Get pool statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<BailianIndexPoolService.PoolStats> getPoolStats() {
        return ResponseEntity.ok(indexPoolService.getPoolStats());
    }

    /**
     * Get available indexes.
     */
    @GetMapping("/available")
    public ResponseEntity<List<BailianIndexPool>> getAvailableIndexes() {
        return ResponseEntity.ok(indexPoolService.getAvailableIndexes());
    }

    /**
     * Get assigned indexes.
     */
    @GetMapping("/assigned")
    public ResponseEntity<List<BailianIndexPool>> getAssignedIndexes() {
        return ResponseEntity.ok(indexPoolService.getAssignedIndexes());
    }

    /**
     * Get index assigned to a specific user.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<BailianIndexPool> getIndexForUser(@PathVariable Long userId) {
        BailianIndexPool index = indexPoolService.getAssignedIndex(userId);
        if (index == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(index);
    }

    /**
     * Manually assign an index to a user.
     */
    @PostMapping("/assign/{userId}")
    public ResponseEntity<Map<String, Object>> assignIndexToUser(@PathVariable Long userId) {
        log.info("Manually assigning index to user: {}", userId);
        try {
            BailianIndexPool assigned = indexPoolService.assignIndexToUser(userId);
            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            result.put("indexId", assigned.getIndexId());
            result.put("categoryId", assigned.getCategoryId());
            result.put("message", "Index assigned successfully");
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Release an index from a user.
     */
    @PostMapping("/release/{userId}")
    public ResponseEntity<Map<String, String>> releaseIndex(@PathVariable Long userId) {
        log.info("Releasing index from user: {}", userId);
        indexPoolService.releaseIndex(userId);
        return ResponseEntity.ok(Map.of("message", "Index released successfully"));
    }

    /**
     * Disable an index.
     */
    @PostMapping("/{id}/disable")
    public ResponseEntity<Map<String, String>> disableIndex(@PathVariable Long id) {
        log.info("Disabling index: {}", id);
        indexPoolService.disableIndex(id);
        return ResponseEntity.ok(Map.of("message", "Index disabled successfully"));
    }

    /**
     * Enable an index.
     */
    @PostMapping("/{id}/enable")
    public ResponseEntity<Map<String, String>> enableIndex(@PathVariable Long id) {
        log.info("Enabling index: {}", id);
        indexPoolService.enableIndex(id);
        return ResponseEntity.ok(Map.of("message", "Index enabled successfully"));
    }

    /**
     * Delete an index from pool.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteIndex(@PathVariable Long id) {
        log.info("Deleting index from pool: {}", id);
        try {
            indexPoolService.deleteIndex(id);
            return ResponseEntity.ok(Map.of("message", "Index deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Request body for adding an index.
     */
    public record AddIndexRequest(String indexId, String categoryId, String indexName) {}
}
