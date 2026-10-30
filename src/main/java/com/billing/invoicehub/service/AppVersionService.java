package com.billing.invoicehub.service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class AppVersionService {
    private static final Logger log = LoggerFactory.getLogger(AppVersionService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private String version = "1.4.0";
    private String gitCommitId = "";
    private String gitCommitShort = "";
    private String buildTime = "";
    private String formattedBuildDate = "30 Oct 2026";
    private String versionFormatted = "v1.4.0 · 30 Oct 2026";

    @Autowired(required = false)
    private BuildProperties buildProperties;

    @Autowired(required = false)
    private GitProperties gitProperties;

    @PostConstruct
    public void init() {
        // 1. Try BuildProperties bean
        if (buildProperties != null) {
            if (buildProperties.getVersion() != null && !buildProperties.getVersion().isBlank()) {
                this.version = buildProperties.getVersion();
            }
            if (buildProperties.getTime() != null) {
                Instant time = buildProperties.getTime();
                this.buildTime = time.toString();
                this.formattedBuildDate = DATE_FORMATTER.format(time.atZone(ZoneId.systemDefault()).toLocalDate());
            }
        } else {
            // Fallback: manually read META-INF/build-info.properties if present
            try {
                ClassPathResource resource = new ClassPathResource("META-INF/build-info.properties");
                if (resource.exists()) {
                    Properties props = new Properties();
                    try (InputStream is = resource.getInputStream()) {
                        props.load(is);
                    }
                    if (props.getProperty("build.version") != null) {
                        this.version = props.getProperty("build.version");
                    }
                    if (props.getProperty("build.time") != null) {
                        String timeStr = props.getProperty("build.time");
                        this.buildTime = timeStr;
                        try {
                            Instant inst = Instant.parse(timeStr);
                            this.formattedBuildDate = DATE_FORMATTER.format(inst.atZone(ZoneId.systemDefault()).toLocalDate());
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ex) {
                log.debug("No build-info.properties found: {}", ex.getMessage());
            }
        }

        // 2. Try GitProperties bean
        if (gitProperties != null) {
            if (gitProperties.getCommitId() != null) {
                this.gitCommitId = gitProperties.getCommitId();
            }
            if (gitProperties.getShortCommitId() != null) {
                this.gitCommitShort = gitProperties.getShortCommitId();
            }
        } else {
            // Fallback: manually read git.properties if present
            try {
                ClassPathResource resource = new ClassPathResource("git.properties");
                if (resource.exists()) {
                    Properties props = new Properties();
                    try (InputStream is = resource.getInputStream()) {
                        props.load(is);
                    }
                    if (props.getProperty("git.commit.id") != null) {
                        this.gitCommitId = props.getProperty("git.commit.id");
                    }
                    if (props.getProperty("git.commit.id.abbrev") != null) {
                        this.gitCommitShort = props.getProperty("git.commit.id.abbrev");
                    }
                }
            } catch (Exception ex) {
                log.debug("No git.properties found: {}", ex.getMessage());
            }
        }

        if (this.formattedBuildDate == null || this.formattedBuildDate.isBlank()) {
            this.formattedBuildDate = DATE_FORMATTER.format(LocalDate.now());
        }

        this.versionFormatted = "v" + this.version + " · " + this.formattedBuildDate;
        log.info("Initialized AppVersionService: {} (commit: {})", this.versionFormatted, this.gitCommitShort);
    }

    public String getVersion() {
        return version;
    }

    public String getGitCommitId() {
        return gitCommitId;
    }

    public String getGitCommitShort() {
        return gitCommitShort;
    }

    public String getBuildTime() {
        return buildTime;
    }

    public String getFormattedBuildDate() {
        return formattedBuildDate;
    }

    public String getVersionFormatted() {
        return versionFormatted;
    }
}
