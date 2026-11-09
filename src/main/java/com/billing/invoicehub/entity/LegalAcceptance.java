package com.billing.invoicehub.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "legal_acceptance", indexes = {
        @Index(name = "idx_legal_acc_user", columnList = "username")
})
public class LegalAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", length = 100, nullable = false)
    private String username;

    @Column(name = "terms_version", length = 20, nullable = false)
    private String termsVersion;

    @Column(name = "privacy_version", length = 20, nullable = false)
    private String privacyVersion;

    @Column(name = "accepted_at", nullable = false)
    private LocalDateTime acceptedAt = LocalDateTime.now();

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    public LegalAcceptance() {
    }

    public LegalAcceptance(String username, String termsVersion, String privacyVersion, String ipAddress) {
        this.username = username;
        this.termsVersion = termsVersion;
        this.privacyVersion = privacyVersion;
        this.ipAddress = ipAddress;
        this.acceptedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public void setTermsVersion(String termsVersion) {
        this.termsVersion = termsVersion;
    }

    public String getPrivacyVersion() {
        return privacyVersion;
    }

    public void setPrivacyVersion(String privacyVersion) {
        this.privacyVersion = privacyVersion;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
}
