package com.billing.invoicehub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_quote_conversion")
public class AiQuoteConversion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser user;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false, length = 1000)
    private String originalFileUrl;

    @Column(columnDefinition = "LONGTEXT")
    private String extractedJson;

    private String generatedQuoteFileName;

    @Column(length = 1000)
    private String generatedQuoteFileUrl;

    @Column(nullable = false)
    private LocalDateTime processingDate;

    @Column(nullable = false, length = 50)
    private String status;

    private Long processingTimeMs = 0L;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private Integer version = 1;

    @Column(columnDefinition = "LONGTEXT")
    private String versionHistory;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getOriginalFileUrl() {
        return originalFileUrl;
    }

    public void setOriginalFileUrl(String originalFileUrl) {
        this.originalFileUrl = originalFileUrl;
    }

    public String getExtractedJson() {
        return extractedJson;
    }

    public void setExtractedJson(String extractedJson) {
        this.extractedJson = extractedJson;
    }

    public String getGeneratedQuoteFileName() {
        return generatedQuoteFileName;
    }

    public void setGeneratedQuoteFileName(String generatedQuoteFileName) {
        this.generatedQuoteFileName = generatedQuoteFileName;
    }

    public String getGeneratedQuoteFileUrl() {
        return generatedQuoteFileUrl;
    }

    public void setGeneratedQuoteFileUrl(String generatedQuoteFileUrl) {
        this.generatedQuoteFileUrl = generatedQuoteFileUrl;
    }

    public LocalDateTime getProcessingDate() {
        return processingDate;
    }

    public void setProcessingDate(LocalDateTime processingDate) {
        this.processingDate = processingDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(Long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getVersionHistory() {
        return versionHistory;
    }

    public void setVersionHistory(String versionHistory) {
        this.versionHistory = versionHistory;
    }
}
