package com.billing.invoicehub.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_quote_message")
public class AiQuoteMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AiQuoteSession session;

    @Column(nullable = false, length = 20)
    private String sender; // USER, ASSISTANT, SYSTEM

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "attachment_url", length = 1000)
    private String attachmentUrl;

    @Column(name = "attachment_filename")
    private String attachmentFilename;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AiQuoteSession getSession() {
        return session;
    }

    public void setSession(AiQuoteSession session) {
        this.session = session;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
