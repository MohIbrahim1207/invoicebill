package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    List<AiUsageLog> findAllByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countBySuccessFalseAndCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COALESCE(SUM(l.totalTokens), 0) FROM AiUsageLog l WHERE l.createdAt >= :start AND l.createdAt <= :end")
    Long sumTotalTokensBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(l.estimatedCostIdr), 0) FROM AiUsageLog l WHERE l.createdAt >= :start AND l.createdAt <= :end")
    BigDecimal sumCostIdrBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(l.estimatedCostUsd), 0) FROM AiUsageLog l WHERE l.createdAt >= :start AND l.createdAt <= :end")
    BigDecimal sumCostUsdBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT l.feature, COUNT(l), COALESCE(SUM(l.inputTokens), 0), COALESCE(SUM(l.outputTokens), 0), COALESCE(SUM(l.estimatedCostIdr), 0) " +
            "FROM AiUsageLog l WHERE l.createdAt >= :start AND l.createdAt <= :end GROUP BY l.feature")
    List<Object[]> aggregateByFeatureBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(l.username, 'anonymous'), COALESCE(l.role, 'UNKNOWN'), COUNT(l), COALESCE(SUM(l.totalTokens), 0), COALESCE(SUM(l.estimatedCostIdr), 0) " +
            "FROM AiUsageLog l WHERE l.createdAt >= :start AND l.createdAt <= :end GROUP BY l.username, l.role ORDER BY SUM(l.estimatedCostIdr) DESC")
    List<Object[]> aggregateByUserBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT l.errorType, COUNT(l) FROM AiUsageLog l WHERE l.success = false AND l.createdAt >= :start AND l.createdAt <= :end GROUP BY l.errorType")
    List<Object[]> aggregateFailuresByErrorTypeBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
