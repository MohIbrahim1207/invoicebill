package com.billing.invoicehub.controller;

import com.billing.invoicehub.entity.AuditLog;
import com.billing.invoicehub.service.AuditLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin/audit-logs")
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    public AdminAuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public String viewAuditLogs(
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model
    ) {
        List<AuditLog> logs = auditLogService.searchLogs(username, action, startDate, endDate);
        model.addAttribute("logs", logs);
        model.addAttribute("username", username);
        model.addAttribute("action", action);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        return "admin-audit-log";
    }
}
