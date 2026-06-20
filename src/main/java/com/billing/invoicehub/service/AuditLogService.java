package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.AuditLog;
import com.billing.invoicehub.repository.AuditLogRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void log(String action, String entityType, Long entityId, String oldValue, String newValue) {
        log(getCurrentUsername(), getCurrentRole(), action, entityType, entityId, oldValue, newValue);
    }

    @Transactional
    public void log(String username, String role, String action, String entityType, Long entityId, String oldValue, String newValue) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUsername(username);
        auditLog.setRole(role);
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setOldValue(oldValue);
        auditLog.setNewValue(newValue);
        auditLog.setIpAddress(getClientIp());
        auditLog.setCreatedAt(LocalDateTime.now());
        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> searchLogs(String username, String action, LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(LocalTime.MAX) : null;
        return auditLogRepository.searchLogs(username, action, startDateTime, endDateTime);
    }

    private String getClientIp() {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes sra) {
                HttpServletRequest request = sra.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                return ip;
            }
        } catch (Exception e) {
            // ignore
        }
        return "N/A";
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return auth.getName();
        }
        return "ANONYMOUS";
    }

    private String getCurrentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.joining(","));
        }
        return "ROLE_ANONYMOUS";
    }
}
