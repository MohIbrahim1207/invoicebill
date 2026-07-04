package com.billing.invoicehub.dto;

import com.billing.invoicehub.entity.Notification;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class NotificationDto {
    private Long id;
    private String title;
    private String message;
    private String type;
    private boolean isRead;
    private LocalDateTime createdAt;
    private String relativeTime;
    private Long referenceId;
    private String referenceType;

    public NotificationDto() {}

    public NotificationDto(Notification n) {
        this.id = n.getId();
        this.title = n.getTitle();
        this.message = n.getMessage();
        this.type = n.getType() != null ? n.getType().name() : "INFO";
        this.isRead = n.isRead();
        this.createdAt = n.getCreatedAt();
        this.relativeTime = getRelativeTimeString(n.getCreatedAt());
        this.referenceId = n.getReferenceId();
        this.referenceType = n.getReferenceType();
    }

    public static String getRelativeTimeString(LocalDateTime dateTime) {
        if (dateTime == null) return "-";
        LocalDateTime now = LocalDateTime.now();
        long seconds = ChronoUnit.SECONDS.between(dateTime, now);
        if (seconds < 60) {
            return "Just now";
        }
        long minutes = ChronoUnit.MINUTES.between(dateTime, now);
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = ChronoUnit.HOURS.between(dateTime, now);
        if (hours < 24) {
            return hours + "h ago";
        }
        long days = ChronoUnit.DAYS.between(dateTime, now);
        return days + "d ago";
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getRelativeTime() { return relativeTime; }
    public void setRelativeTime(String relativeTime) { this.relativeTime = relativeTime; }
    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
}
