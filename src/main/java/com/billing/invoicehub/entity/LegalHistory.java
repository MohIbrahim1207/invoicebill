package com.billing.invoicehub.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "legal_history")
public class LegalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slug", length = 50, nullable = false)
    private String slug;

    @Column(name = "version", length = 20, nullable = false)
    private String version;

    @Column(name = "title_en", length = 200)
    private String titleEn;

    @Column(name = "title_id", length = 200)
    private String titleId;

    @Column(name = "content_en", columnDefinition = "LONGTEXT")
    private String contentEn;

    @Column(name = "content_id", columnDefinition = "LONGTEXT")
    private String contentId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    public LegalHistory() {
    }

    public LegalHistory(String slug, String version, String titleEn, String titleId, String contentEn, String contentId, String createdBy) {
        this.slug = slug;
        this.version = version;
        this.titleEn = titleEn;
        this.titleId = titleId;
        this.contentEn = contentEn;
        this.contentId = contentId;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public void setTitleEn(String titleEn) {
        this.titleEn = titleEn;
    }

    public String getTitleId() {
        return titleId;
    }

    public void setTitleId(String titleId) {
        this.titleId = titleId;
    }

    public String getContentEn() {
        return contentEn;
    }

    public void setContentEn(String contentEn) {
        this.contentEn = contentEn;
    }

    public String getContentId() {
        return contentId;
    }

    public void setContentId(String contentId) {
        this.contentId = contentId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
