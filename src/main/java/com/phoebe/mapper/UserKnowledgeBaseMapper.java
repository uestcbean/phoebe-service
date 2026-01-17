package com.phoebe.mapper;

import com.phoebe.entity.UserKnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserKnowledgeBaseMapper {

    /**
     * Insert a new user knowledge base record
     */
    int insert(UserKnowledgeBase userKnowledgeBase);

    /**
     * Update an existing record
     */
    int update(UserKnowledgeBase userKnowledgeBase);

    /**
     * Find by ID
     */
    UserKnowledgeBase findById(@Param("id") Long id);

    /**
     * Find by userId
     */
    UserKnowledgeBase findByUserId(@Param("userId") Long userId);

    /**
     * Find by indexId
     */
    UserKnowledgeBase findByIndexId(@Param("indexId") String indexId);

    /**
     * Find all records
     */
    List<UserKnowledgeBase> findAll();

    /**
     * Delete by ID
     */
    int deleteById(@Param("id") Long id);

    /**
     * Delete by userId
     */
    int deleteByUserId(@Param("userId") Long userId);
}
