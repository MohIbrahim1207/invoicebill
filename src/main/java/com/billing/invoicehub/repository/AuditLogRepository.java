package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:username IS NULL OR :username = '' OR LOWER(a.username) LIKE LOWER(CONCAT('%', :username, '%'))) AND " +
           "(:action IS NULL OR :action = '' OR LOWER(a.action) LIKE LOWER(CONCAT('%', :action, '%'))) AND " +
           "(:startDate IS NULL OR a.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR a.createdAt <= :endDate) " +
           "ORDER BY a.createdAt DESC")
    List<AuditLog> searchLogs(
            @Param("username") String username,
            @Param("action") String action,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}
