package com.phoebe.repository;

import com.phoebe.entity.UserKnowledgeBase;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface UserKnowledgeBaseRepository extends ReactiveCrudRepository<UserKnowledgeBase, String> {

    /**
     * Find knowledge base by user ID
     */
    Mono<UserKnowledgeBase> findByUserId(String userId);

    /**
     * Find knowledge base by index ID
     */
    Mono<UserKnowledgeBase> findByIndexId(String indexId);

    /**
     * Find all active knowledge bases
     */
    Flux<UserKnowledgeBase> findByStatus(String status);

    /**
     * Find active knowledge bases
     */
    default Flux<UserKnowledgeBase> findAllActive() {
        return findByStatus(UserKnowledgeBase.STATUS_ACTIVE);
    }

    /**
     * Check if user has a knowledge base
     */
    Mono<Boolean> existsByUserId(String userId);
}

