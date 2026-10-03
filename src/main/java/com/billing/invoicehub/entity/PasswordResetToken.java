package com.billing.invoicehub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class PasswordResetToken {

    /** Maximum allowed failed OTP verification attempts before the token is locked. */
    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    private String otp;
    private LocalDateTime expiryDate;
    private LocalDateTime createdAt;
    private boolean used;

    /** SEC-004: Tracks failed verification attempts. Token is locked when this reaches MAX_ATTEMPTS. */
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    public PasswordResetToken() {}

    public PasswordResetToken(String email, String otp, LocalDateTime expiryDate) {
        this.email = email;
        this.otp = otp;
        this.expiryDate = expiryDate;
        this.createdAt = LocalDateTime.now();
        this.used = false;
        this.attemptCount = 0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
    public LocalDateTime getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDateTime expiryDate) { this.expiryDate = expiryDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

    /** Increments the attempt counter and returns the updated count. */
    public int incrementAttempts() { return ++this.attemptCount; }

    /** Returns true when the token has been used or has exceeded the maximum attempt limit. */
    public boolean isLocked() { return used || attemptCount >= MAX_ATTEMPTS; }

    public boolean isExpired() { return expiryDate != null && LocalDateTime.now().isAfter(expiryDate); }
}


