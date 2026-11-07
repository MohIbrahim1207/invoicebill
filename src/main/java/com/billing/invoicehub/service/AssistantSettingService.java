package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.SystemSetting;
import com.billing.invoicehub.repository.SystemSettingRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AssistantSettingService {

    private static final Logger log = LoggerFactory.getLogger(AssistantSettingService.class);

    public static final String KEY_ASSISTANT_VENDORS_ENABLED = "assistant_vendors_enabled";

    private final SystemSettingRepository settingRepository;
    private final AuditLogService auditLogService;

    private volatile boolean assistantVendorsEnabled = false; // Default OFF

    public AssistantSettingService(SystemSettingRepository settingRepository,
                                   @Autowired(required = false) AuditLogService auditLogService) {
        this.settingRepository = settingRepository;
        this.auditLogService = auditLogService;
    }

    @PostConstruct
    public void init() {
        refreshFromDatabase();
    }

    public synchronized void refreshFromDatabase() {
        try {
            Optional<SystemSetting> opt = settingRepository.findById(KEY_ASSISTANT_VENDORS_ENABLED);
            if (opt.isPresent()) {
                this.assistantVendorsEnabled = "true".equalsIgnoreCase(opt.get().getValue());
            } else {
                this.assistantVendorsEnabled = false;
            }
            log.info("Initialized AssistantSettingService: assistantVendorsEnabled={}", assistantVendorsEnabled);
        } catch (Exception ex) {
            log.warn("Could not load assistant vendor settings from database: {}. Defaulting to false.", ex.getMessage());
            this.assistantVendorsEnabled = false;
        }
    }

    public boolean isAssistantForVendorsEnabled() {
        return assistantVendorsEnabled;
    }

    @Transactional
    public synchronized void setAssistantForVendorsEnabled(boolean enabled, String updatedBy) {
        this.assistantVendorsEnabled = enabled;
        try {
            SystemSetting setting = settingRepository.findById(KEY_ASSISTANT_VENDORS_ENABLED)
                    .orElseGet(() -> new SystemSetting(KEY_ASSISTANT_VENDORS_ENABLED, String.valueOf(enabled), updatedBy));
            setting.setValue(String.valueOf(enabled));
            setting.setUpdatedBy(updatedBy != null ? updatedBy : "super_admin");
            setting.setUpdatedAt(LocalDateTime.now());
            settingRepository.save(setting);

            if (auditLogService != null) {
                auditLogService.log(
                        updatedBy != null ? updatedBy : "super_admin",
                        "ROLE_SUPER_ADMIN",
                        "UPDATE_SYSTEM_SETTING",
                        "SYSTEM_SETTING",
                        null,
                        null,
                        "Updated 'Assistant for vendors' to: " + (enabled ? "ON" : "OFF")
                );
            }
            log.info("Super admin '{}' updated assistantVendorsEnabled to {}", updatedBy, enabled);
        } catch (Exception ex) {
            log.error("Failed to persist assistant vendor setting: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to save assistant vendor setting: " + ex.getMessage(), ex);
        }
    }
}

