package com.phoebe.mapper;

import com.phoebe.entity.Note;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NoteMapper {

    /**
     * Insert a new note
     */
    int insert(Note note);

    /**
     * Update an existing note
     */
    int update(Note note);

    /**
     * Find note by ID
     */
    Note findById(@Param("id") Long id);

    /**
     * Find note by ID and userId (for ownership verification)
     */
    Note findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Find all notes
     */
    List<Note> findAll();

    /**
     * Find notes by userId and status
     */
    List<Note> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") Integer status);

    /**
     * Find active notes for a user
     */
    default List<Note> findActiveByUserId(Long userId) {
        return findByUserIdAndStatus(userId, Note.STATUS_ACTIVE);
    }

    /**
     * Find notes by source and status
     */
    List<Note> findBySourceAndStatus(@Param("source") String source, @Param("status") Integer status);

    /**
     * Find notes by userId, source and status
     */
    List<Note> findByUserIdAndSourceAndStatus(@Param("userId") Long userId, 
                                               @Param("source") String source, 
                                               @Param("status") Integer status);

    /**
     * Find notes by date range and status
     */
    List<Note> findByCreatedAtBetweenAndStatus(@Param("start") LocalDateTime start, 
                                                @Param("end") LocalDateTime end, 
                                                @Param("status") Integer status);

    /**
     * Find notes by userId, date range and status
     */
    List<Note> findByUserIdAndCreatedAtBetweenAndStatus(@Param("userId") Long userId, 
                                                         @Param("start") LocalDateTime start, 
                                                         @Param("end") LocalDateTime end, 
                                                         @Param("status") Integer status);

    /**
     * Delete note by ID (hard delete)
     */
    int deleteById(@Param("id") Long id);
}
