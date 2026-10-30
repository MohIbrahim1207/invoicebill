package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.SystemSetting;
import com.billing.invoicehub.repository.SystemSettingRepository;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceBannerService {
    private static final Logger log = LoggerFactory.getLogger(MaintenanceBannerService.class);

    public static final String KEY_ENABLED = "maintenance_banner_enabled";
    public static final String KEY_MSG_EN = "maintenance_banner_msg_en";
    public static final String KEY_MSG_ID = "maintenance_banner_msg_id";
    public static final String KEY_TYPE = "maintenance_banner_type";

    public static final String DEFAULT_MSG_EN = "System update today at 20:00 WIB, the site may be unavailable for 1 minute";
    public static final String DEFAULT_MSG_ID = "Pembaruan sistem hari ini pukul 20:00 WIB, situs mungkin tidak dapat diakses selama 1 menit";

    private final SystemSettingRepository settingRepository;

    private volatile boolean bannerActive = false;
    private volatile String bannerMessageEn = DEFAULT_MSG_EN;
    private volatile String bannerMessageId = DEFAULT_MSG_ID;
    private volatile String bannerType = "warning";

    public MaintenanceBannerService(SystemSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @PostConstruct
    public void init() {
        refreshFromDatabase();
    }

    public synchronized void refreshFromDatabase() {
        try {
            Optional<SystemSetting> enabledOpt = settingRepository.findById(KEY_ENABLED);
            this.bannerActive = enabledOpt.map(s -> "true".equalsIgnoreCase(s.getValue())).orElse(false);

            Optional<SystemSetting> msgEnOpt = settingRepository.findById(KEY_MSG_EN);
            this.bannerMessageEn = msgEnOpt.map(SystemSetting::getValue).filter(s -> !s.isBlank()).orElse(DEFAULT_MSG_EN);

            Optional<SystemSetting> msgIdOpt = settingRepository.findById(KEY_MSG_ID);
            this.bannerMessageId = msgIdOpt.map(SystemSetting::getValue).filter(s -> !s.isBlank()).orElse(DEFAULT_MSG_ID);

            Optional<SystemSetting> typeOpt = settingRepository.findById(KEY_TYPE);
            this.bannerType = typeOpt.map(SystemSetting::getValue).filter(s -> !s.isBlank()).orElse("warning");

            log.info("Initialized MaintenanceBannerService: active={}, type={}", bannerActive, bannerType);
        } catch (Exception ex) {
            log.warn("Could not load maintenance banner settings from database: {}", ex.getMessage());
        }
    }

    public boolean isBannerActive() {
        return bannerActive;
    }

    public String getBannerMessageEn() {
        return bannerMessageEn;
    }

    public String getBannerMessageId() {
        return bannerMessageId;
    }

    public String getBannerType() {
        return bannerType;
    }

    @Transactional
    public synchronized void updateBanner(boolean enabled, String messageEn, String messageId, String type, String updatedBy) {
        String cleanEn = (messageEn == null || messageEn.isBlank()) ? DEFAULT_MSG_EN : messageEn.trim();
        String cleanId = (messageId == null || messageId.isBlank()) ? DEFAULT_MSG_ID : messageId.trim();
        String cleanType = (type == null || type.isBlank()) ? "warning" : type.trim().toLowerCase();

        saveSetting(KEY_ENABLED, String.valueOf(enabled), updatedBy);
        saveSetting(KEY_MSG_EN, cleanEn, updatedBy);
        saveSetting(KEY_MSG_ID, cleanId, updatedBy);
        saveSetting(KEY_TYPE, cleanType, updatedBy);

        this.bannerActive = enabled;
        this.bannerMessageEn = cleanEn;
        this.bannerMessageId = cleanId;
        this.bannerType = cleanType;

        log.info("Updated maintenance banner by {}: enabled={}, type={}", updatedBy, enabled, cleanType);
    }

    private void saveSetting(String key, String value, String updatedBy) {
        SystemSetting setting = settingRepository.findById(key)
                .orElse(new SystemSetting(key, value, updatedBy));
        setting.setValue(value);
        setting.setUpdatedBy(updatedBy);
        setting.setUpdatedAt(LocalDateTime.now());
        settingRepository.save(setting);
    }
}
