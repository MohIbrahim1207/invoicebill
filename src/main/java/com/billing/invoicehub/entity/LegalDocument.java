package com.billing.invoicehub.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "legal_document")
public class LegalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slug", length = 50, nullable = false, unique = true)
    private String slug; // "terms", "privacy", "cookies"

    @Column(name = "version", length = 20, nullable = false)
    private String version = "1.0";

    @Column(name = "title_en", length = 200, nullable = false)
    private String titleEn;

    @Column(name = "title_id", length = 200, nullable = false)
    private String titleId;

    @Column(name = "content_en", columnDefinition = "LONGTEXT", nullable = false)
    private String contentEn;

    @Column(name = "content_id", columnDefinition = "LONGTEXT", nullable = false)
    private String contentId;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "updated_by", length = 100)
    private String updatedBy = "system";

    public LegalDocument() {
    }

    public LegalDocument(String slug, String version, String titleEn, String titleId, String contentEn, String contentId, String updatedBy) {
        this.slug = slug;
        this.version = version;
        this.titleEn = titleEn;
        this.titleId = titleId;
        this.contentEn = contentEn;
        this.contentId = contentId;
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
