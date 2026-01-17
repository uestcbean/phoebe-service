package com.phoebe.mapper;

import com.phoebe.entity.NoteSyncHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NoteSyncHistoryMapper {

    /**
     * Insert a new sync history record
     */
    int insert(NoteSyncHistory noteSyncHistory);

    /**
     * Update an existing record
     */
    int update(NoteSyncHistory noteSyncHistory);

    /**
     * Find by ID
     */
    NoteSyncHistory findById(@Param("id") Long id);

    /**
     * Find by noteId
     */
    List<NoteSyncHistory> findByNoteId(@Param("noteId") Long noteId);

    /**
     * Find by userId
     */
    List<NoteSyncHistory> findByUserId(@Param("userId") Long userId);

    /**
     * Find by sync status
     */
    List<NoteSyncHistory> findBySyncStatus(@Param("syncStatus") String syncStatus);

    /**
     * Find latest sync record for a note
     */
    NoteSyncHistory findLatestByNoteId(@Param("noteId") Long noteId);

    /**
     * Check if a note has been successfully synced
     */
    default boolean isNoteSynced(Long noteId) {
        NoteSyncHistory history = findLatestByNoteId(noteId);
        return history != null && NoteSyncHistory.STATUS_SUCCESS.equals(history.getSyncStatus());
    }

    /**
     * Find latest successful sync history with document ID
     */
    NoteSyncHistory findLatestSuccessfulByNoteId(@Param("noteId") Long noteId);

    /**
     * Find all records
     */
    List<NoteSyncHistory> findAll();

    /**
     * Delete by ID
     */
    int deleteById(@Param("id") Long id);

    /**
     * Delete by noteId
     */
    int deleteByNoteId(@Param("noteId") Long noteId);
}
