/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.billing.invoicehub.service.CloudinaryService
 *  org.springframework.beans.factory.annotation.Autowired
 *  org.springframework.stereotype.Service
 *  org.springframework.web.multipart.MultipartFile
 */
package com.billing.invoicehub.service;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Backward-compatible wrapper for legacy callers.
 * All uploads are delegated to Cloudinary; no local filesystem storage is used.
 */
@Service
public class FileStorageService {
    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);

    @Autowired(required = false)
    private CloudinaryService cloudinaryService;

    public String storeInvoiceFile(MultipartFile file) throws IOException {
        ensureCloudinaryConfigured();
        logger.debug("Delegating invoice upload to Cloudinary");
        return cloudinaryService.uploadInvoiceFile(file);
    }

    public String storeSupportingDocument(MultipartFile file) throws IOException {
        ensureCloudinaryConfigured();
        logger.debug("Delegating supporting document upload to Cloudinary");
        return cloudinaryService.uploadSupportingDocument(file);
    }

    public String storeVendorDocument(MultipartFile file) throws IOException {
        ensureCloudinaryConfigured();
        logger.debug("Delegating vendor document upload to Cloudinary");
        return cloudinaryService.uploadVendorDocument(file);
    }

    public String storeDocument(MultipartFile file) throws IOException {
        ensureCloudinaryConfigured();
        logger.debug("Delegating document upload to Cloudinary");
        return cloudinaryService.uploadDocument(file);
    }

    public String extractObjectPathFromUrl(String fileUrl) {
        ensureCloudinaryConfigured();
        logger.debug("Extracting object path from URL");
        return cloudinaryService.extractPublicIdFromUrl(fileUrl);
    }

    private void ensureCloudinaryConfigured() {
        if (cloudinaryService == null) {
            throw new IllegalStateException("Cloudinary file upload service is not configured");
        }
    }

    public boolean deleteFile(String fileUrl) {
        ensureCloudinaryConfigured();
        return cloudinaryService.deleteFile(fileUrl);
    }
}
