package com.billing.invoicehub.dto;

import java.util.List;

public class AiQuoteSessionDto {
    private Long id;
    private String title;
    private String status;
    private String generatedPdfUrl;
    private String generatedPdfFilename;
    private String generatedDocxUrl;
    private String generatedDocxFilename;
    private String createdAt;
    private String updatedAt;
    private AiQuotationEditDto structuredQuoteData;
    private List<AiChatMessageDto> messages;

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

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public AiQuotationEditDto getStructuredQuoteData() {
        return structuredQuoteData;
    }

    public void setStructuredQuoteData(AiQuotationEditDto structuredQuoteData) {
        this.structuredQuoteData = structuredQuoteData;
    }

    public List<AiChatMessageDto> getMessages() {
        return messages;
    }

    public void setMessages(List<AiChatMessageDto> messages) {
        this.messages = messages;
    }
}
