/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.service.FileStorageService
 *  org.springframework.beans.factory.annotation.Value
 *  org.springframework.stereotype.Service
 *  org.springframework.web.multipart.MultipartFile
 */
package com.billing.invoicehub.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "webp");
    private final Path uploadRoot;
    private final Path invoiceDir;
    private final Path supportingDocumentDir;
    private final Path vendorDocumentDir;

    public FileStorageService(@Value(value="${app.upload-dir:uploads}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir, new String[0]).toAbsolutePath().normalize();
        this.invoiceDir = this.uploadRoot.resolve("invoices").normalize();
        this.supportingDocumentDir = this.uploadRoot.resolve("supporting-documents").normalize();
        this.vendorDocumentDir = this.uploadRoot.resolve("vendor-documents").normalize();
        try {
            Files.createDirectories(this.invoiceDir, new FileAttribute[0]);
            Files.createDirectories(this.supportingDocumentDir, new FileAttribute[0]);
            Files.createDirectories(this.vendorDocumentDir, new FileAttribute[0]);
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not initialize upload directories", e);
        }
    }

    public String storeInvoiceFile(MultipartFile file) throws IOException {
        return this.store(file, this.invoiceDir);
    }

    public String storeSupportingDocument(MultipartFile file) throws IOException {
        return this.store(file, this.supportingDocumentDir);
    }

    public String storeVendorDocument(MultipartFile file) throws IOException {
        return this.store(file, this.vendorDocumentDir);
    }

    private String store(MultipartFile file, Path directory) throws IOException {
        this.validateFileType(file);
        String originalName = file.getOriginalFilename();
        String cleanName = originalName == null || originalName.isBlank() ? "upload" : Paths.get(originalName, new String[0]).getFileName().toString();
        cleanName = cleanName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String storedName = String.valueOf(UUID.randomUUID()) + "_" + cleanName;
        Path target = directory.resolve(storedName).normalize();
        file.transferTo(target.toFile());
        return storedName;
    }

    private void validateFileType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.contains(".")) {
            throw new IllegalArgumentException("Unsupported file type. Allowed: PDF, PNG, JPG, JPEG, WEBP");
        }
        String extension = originalName.substring(originalName.lastIndexOf(46) + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported file type. Allowed: PDF, PNG, JPG, JPEG, WEBP");
        }
    }
}

