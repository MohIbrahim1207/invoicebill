package com.billing.invoicehub.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ai_quote_session")
public class AiQuoteSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title = "New Quotation Assistant";

    @Column(nullable = false, length = 50)
    private String status = "DRAFT";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private AppUser createdBy;

    @Column(name = "structured_quote_json", columnDefinition = "LONGTEXT")
    private String structuredQuoteJson;

    @Column(name = "generated_pdf_url", length = 1000)
    private String generatedPdfUrl;

    @Column(name = "generated_pdf_filename")
    private String generatedPdfFilename;

    @Column(name = "generated_docx_url", length = 1000)
    private String generatedDocxUrl;

    @Column(name = "generated_docx_filename")
    private String generatedDocxFilename;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<AiQuoteMessage> messages = new ArrayList<>();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(AppUser createdBy) {
        this.createdBy = createdBy;
    }

    public String getStructuredQuoteJson() {
        return structuredQuoteJson;
    }

    public void setStructuredQuoteJson(String structuredQuoteJson) {
        this.structuredQuoteJson = structuredQuoteJson;
    }

    public String getGeneratedPdfUrl() {
        return generatedPdfUrl;
    }

    public void setGeneratedPdfUrl(String generatedPdfUrl) {
        this.generatedPdfUrl = generatedPdfUrl;
    }

    public String getGeneratedPdfFilename() {
        return generatedPdfFilename;
    }

    public void setGeneratedPdfFilename(String generatedPdfFilename) {
        this.generatedPdfFilename = generatedPdfFilename;
    }

    public String getGeneratedDocxUrl() {
        return generatedDocxUrl;
    }

    public void setGeneratedDocxUrl(String generatedDocxUrl) {
        this.generatedDocxUrl = generatedDocxUrl;
    }

    public String getGeneratedDocxFilename() {
        return generatedDocxFilename;
    }

    public void setGeneratedDocxFilename(String generatedDocxFilename) {
        this.generatedDocxFilename = generatedDocxFilename;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<AiQuoteMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<AiQuoteMessage> messages) {
        this.messages = messages;
    }
}
