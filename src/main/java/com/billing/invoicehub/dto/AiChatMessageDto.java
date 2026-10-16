package com.billing.invoicehub.dto;

import java.util.List;

public class AiChatMessageDto {
    private Long id;
    private String sender; // USER, ASSISTANT, SYSTEM
    private String content;
    private String attachmentUrl;
    private String attachmentFilename;
    private String createdAt;

    // Structured Quote Review payloads
    private AiQuotationEditDto quoteData;
    private List<String> missingFields;
    private boolean quoteComplete;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAttachmentUrl() {
        return attachmentUrl;
    }

    public void setAttachmentUrl(String attachmentUrl) {
        this.attachmentUrl = attachmentUrl;
    }

    public String getAttachmentFilename() {
        return attachmentFilename;
    }

    public void setAttachmentFilename(String attachmentFilename) {
        this.attachmentFilename = attachmentFilename;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public AiQuotationEditDto getQuoteData() {
        return quoteData;
    }

    public void setQuoteData(AiQuotationEditDto quoteData) {
        this.quoteData = quoteData;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }

    public void setMissingFields(List<String> missingFields) {
        this.missingFields = missingFields;
    }

    public boolean isQuoteComplete() {
        return quoteComplete;
    }

    public void setQuoteComplete(boolean quoteComplete) {
        this.quoteComplete = quoteComplete;
    }
}
