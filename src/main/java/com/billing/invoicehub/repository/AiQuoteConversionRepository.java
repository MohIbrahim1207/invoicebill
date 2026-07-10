package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.AiQuoteConversion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiQuoteConversionRepository extends JpaRepository<AiQuoteConversion, Long> {

    @Query("SELECT q FROM AiQuoteConversion q JOIN FETCH q.user WHERE q.id = :id")
    Optional<AiQuoteConversion> findByIdWithUser(@Param("id") Long id);

    @Query("SELECT q FROM AiQuoteConversion q JOIN FETCH q.user ORDER BY q.id DESC")
    List<AiQuoteConversion> findAllWithUserOrderByIdDesc();

    long countByStatus(String status);

    @Query("SELECT COUNT(q) FROM AiQuoteConversion q WHERE q.status = 'GENERATED' AND q.processingDate >= :startOfDay")
    long countQuotesGeneratedToday(@Param("startOfDay") LocalDateTime startOfDay);

    @Query("SELECT COALESCE(AVG(q.processingTimeMs), 0.0) FROM AiQuoteConversion q WHERE q.status IN ('GENERATED', 'PENDING_REVIEW')")
    double getAverageProcessingTimeMs();

    @Query("SELECT COUNT(q) FROM AiQuoteConversion q WHERE q.status IN ('GENERATED', 'PENDING_REVIEW')")
    long countSuccessfulConversions();
}
