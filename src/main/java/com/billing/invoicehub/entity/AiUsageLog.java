package com.billing.invoicehub.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tracks operational metadata for Gemini AI API calls.
 * STRICT PRIVACY REQUIREMENT: Do NOT store prompt or response text — only metadata.
 */
@Entity
@Table(name = "ai_usage_log", indexes = {
        @Index(name = "idx_ai_usage_created_at", columnList = "created_at"),
        @Index(name = "idx_ai_usage_username", columnList = "username"),
        @Index(name = "idx_ai_usage_feature", columnList = "feature")
})
public class AiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "role", length = 100)
    private String role;

    @Column(name = "feature", length = 50, nullable = false)
    private String feature; // ASSISTANT_CHAT, QUOTE, PRE_CHECK, OTHER

    @Column(name = "model", length = 50)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens = 0;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens = 0;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens = 0;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs = 0;

    @Column(name = "success", nullable = false)
    private boolean success = true;

    @Column(name = "error_type", length = 100)
    private String errorType;

    @Column(name = "estimated_cost_usd", precision = 12, scale = 6)
    private BigDecimal estimatedCostUsd = BigDecimal.ZERO;

    @Column(name = "estimated_cost_idr", precision = 15, scale = 2)
    private BigDecimal estimatedCostIdr = BigDecimal.ZERO;

    public AiUsageLog() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(int inputTokens) {
        this.inputTokens = inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(int outputTokens) {
        this.outputTokens = outputTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public BigDecimal getEstimatedCostUsd() {
        return estimatedCostUsd;
    }

    public void setEstimatedCostUsd(BigDecimal estimatedCostUsd) {
        this.estimatedCostUsd = estimatedCostUsd;
    }

    public BigDecimal getEstimatedCostIdr() {
        return estimatedCostIdr;
    }

    public void setEstimatedCostIdr(BigDecimal estimatedCostIdr) {
        this.estimatedCostIdr = estimatedCostIdr;
    }
}
