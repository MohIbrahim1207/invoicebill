package com.billing.invoicehub.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;

@Entity
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String message;
    @Enumerated(EnumType.STRING)
    private NotificationType type;
    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser user;
    private Long relatedTicketId;
    private Long relatedInvoiceId;
    private String recipientRole;
    private Long referenceId;
    private String referenceType;
    private boolean isRead;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Notification() {}

    public Notification(String title, String message, NotificationType type, AppUser recipient) {
        this.title = title;
        this.message = message;
        this.type = type;
        this.user = recipient;
        this.createdAt = LocalDateTime.now();
        this.isRead = false;
    }

    public Notification(String title, String message, NotificationType type, String recipientRole) {
        this.title = title;
        this.message = message;
        this.type = type;
        this.recipientRole = recipientRole;
        this.createdAt = LocalDateTime.now();
        this.isRead = false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }
    public NotificationType getNotificationType() { return this.type; }
    public void setNotificationType(NotificationType notificationType) { this.type = notificationType; }
    public AppUser getRecipient() { return user; }
    public void setRecipient(AppUser recipient) { this.user = recipient; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public Long getRelatedTicketId() { return relatedTicketId; }
    public void setRelatedTicketId(Long relatedTicketId) { this.relatedTicketId = relatedTicketId; }
    public Long getRelatedInvoiceId() { return relatedInvoiceId; }
    public void setRelatedInvoiceId(Long relatedInvoiceId) { this.relatedInvoiceId = relatedInvoiceId; }
    public String getRecipientRole() { return recipientRole; }
    public void setRecipientRole(String recipientRole) { this.recipientRole = recipientRole; }
    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { this.isRead = read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}



