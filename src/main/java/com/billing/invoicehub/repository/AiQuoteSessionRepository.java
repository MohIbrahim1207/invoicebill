package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.AiQuoteSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AiQuoteSessionRepository extends JpaRepository<AiQuoteSession, Long> {

    @Query("SELECT s FROM AiQuoteSession s LEFT JOIN FETCH s.createdBy WHERE s.id = :id")
    Optional<AiQuoteSession> findByIdWithUser(@Param("id") Long id);

    @Query("SELECT DISTINCT s FROM AiQuoteSession s LEFT JOIN FETCH s.messages WHERE s.id = :id")
    Optional<AiQuoteSession> findByIdWithMessages(@Param("id") Long id);

    @Query("SELECT s FROM AiQuoteSession s ORDER BY s.updatedAt DESC")
    List<AiQuoteSession> findAllByOrderByUpdatedAtDesc();

    @Query("SELECT s FROM AiQuoteSession s WHERE s.createdBy.id = :userId ORDER BY s.updatedAt DESC")
    List<AiQuoteSession> findByCreatedByIdOrderByUpdatedAtDesc(@Param("userId") Long userId);
}
