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
import java.util.*;

@Service
public class EmailSettingService {

    private static final Logger log = LoggerFactory.getLogger(EmailSettingService.class);

    public static final String KEY_EMAIL_NOTIFICATION_CC = "email_notification_cc";
    public static final String DEFAULT_NOTIFICATION_CC = "umapathi@flow-force.com, operations@flow-force.com";

    private final SystemSettingRepository settingRepository;
    private final AuditLogService auditLogService;

    private volatile String cachedNotificationCc = DEFAULT_NOTIFICATION_CC;

    public EmailSettingService(SystemSettingRepository settingRepository,
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
            Optional<SystemSetting> opt = settingRepository.findById(KEY_EMAIL_NOTIFICATION_CC);
            if (opt.isPresent() && opt.get().getValue() != null && !opt.get().getValue().isBlank()) {
                this.cachedNotificationCc = opt.get().getValue().trim();
            } else {
                this.cachedNotificationCc = DEFAULT_NOTIFICATION_CC;
            }
            log.info("Initialized EmailSettingService: notificationCc='{}'", cachedNotificationCc);
        } catch (Exception ex) {
            log.warn("Could not load email CC setting from database: {}. Using default.", ex.getMessage());
            this.cachedNotificationCc = DEFAULT_NOTIFICATION_CC;
        }
    }

    public String getNotificationCcSetting() {
        return cachedNotificationCc;
    }

    @Transactional
    public synchronized void setNotificationCcSetting(String ccList, String updatedBy) {
        String cleanValue = (ccList != null && !ccList.isBlank()) ? ccList.trim() : DEFAULT_NOTIFICATION_CC;
        this.cachedNotificationCc = cleanValue;
        try {
            SystemSetting setting = settingRepository.findById(KEY_EMAIL_NOTIFICATION_CC)
                    .orElseGet(() -> new SystemSetting(KEY_EMAIL_NOTIFICATION_CC, cleanValue, updatedBy));
            setting.setValue(cleanValue);
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
                        "Updated notification email CC list to: " + cleanValue
                );
            }
            log.info("Super admin '{}' updated email notification CC list to: {}", updatedBy, cleanValue);
        } catch (Exception ex) {
            log.error("Failed to persist notification CC setting: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to save email CC setting: " + ex.getMessage(), ex);
        }
    }

    /**
     * Returns parsed, deduplicated list of CC email addresses for operational notifications.
     * Excludes any address matching the primary recipient.
     */
    public List<String> getNotificationCcRecipients(String recipient) {
        return parseAndFilterCc(cachedNotificationCc, recipient);
    }

    public List<String> parseAndFilterCc(String ccString, String recipient) {
        if (ccString == null || ccString.isBlank()) {
            return Collections.emptyList();
        }
        String normalizedRecipient = recipient != null ? recipient.trim().toLowerCase() : "";
        String[] parts = ccString.split("[,;\\s]+");
        Set<String> cleanSet = new LinkedHashSet<>();
        for (String p : parts) {
            if (p != null) {
                String clean = p.trim().toLowerCase();
                if (!clean.isEmpty() && clean.contains("@")) {
                    if (!clean.equalsIgnoreCase(normalizedRecipient)) {
                        cleanSet.add(clean);
                    }
                }
            }
        }
        return new ArrayList<>(cleanSet);
    }
}

