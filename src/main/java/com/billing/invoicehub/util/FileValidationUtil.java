package com.billing.invoicehub.util;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileValidationUtil {

    private static final Logger log = LoggerFactory.getLogger(FileValidationUtil.class);
    private static final Tika TIKA = new Tika();

    // Default / Invoice / Ticket Allowed Extensions & MIME types
    private static final Set<String> ALLOWED_INVOICE_EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "docx", "xlsx"
    );
    private static final Set<String> ALLOWED_INVOICE_MIME_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/pjpeg",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );
    private static final String ALLOWED_INVOICE_TYPES_DISPLAY = "PDF, PNG, JPG, JPEG, DOCX, XLSX";

    // Vendor Registration Allowed Extensions & MIME types
    private static final Set<String> ALLOWED_REGISTRATION_EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "webp"
    );
    private static final Set<String> ALLOWED_REGISTRATION_MIME_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/pjpeg",
            "image/webp"
    );
    private static final String ALLOWED_REGISTRATION_TYPES_DISPLAY = "PDF, PNG, JPG, JPEG, WEBP";

    public static final long MAX_FILE_SIZE_10MB = 10 * 1024 * 1024; // 10 MB

    public static class FileValidationException extends IllegalArgumentException {
        private final String fieldName;

        public FileValidationException(String fieldName, String message) {
            super(message);
            this.fieldName = fieldName;
        }

        public String getFieldName() {
            return fieldName;
        }
    }

    public static void validateFile(MultipartFile file) {
        validateFile(file, null, MAX_FILE_SIZE_10MB);
    }

    public static void validateFile(MultipartFile file, String fieldName) {
        validateFile(file, fieldName, MAX_FILE_SIZE_10MB);
    }

    public static void validateFile(MultipartFile file, long maxSizeBytes) {
        validateFile(file, null, maxSizeBytes);
    }

    public static void validateFileIfPresent(MultipartFile file) {
        if (file != null && !file.isEmpty()) {
            validateFile(file, null, MAX_FILE_SIZE_10MB);
        }
    }

    public static void validateFileIfPresent(MultipartFile file, String fieldName) {
        if (file != null && !file.isEmpty()) {
            validateFile(file, fieldName, MAX_FILE_SIZE_10MB);
        }
    }

    public static void validateFile(MultipartFile file, String fieldName, long maxSizeBytes) {
        validateWithRules(file, fieldName, maxSizeBytes, ALLOWED_INVOICE_EXTENSIONS,
                ALLOWED_INVOICE_MIME_TYPES, ALLOWED_INVOICE_TYPES_DISPLAY);
    }

    public static void validateRegistrationFile(MultipartFile file, String fieldName) {
        validateRegistrationFile(file, fieldName, MAX_FILE_SIZE_10MB);
    }

    public static void validateRegistrationFileIfPresent(MultipartFile file, String fieldName) {
        if (file != null && !file.isEmpty()) {
            validateRegistrationFile(file, fieldName, MAX_FILE_SIZE_10MB);
        }
    }

    public static void validateRegistrationFile(MultipartFile file, String fieldName, long maxSizeBytes) {
        validateWithRules(file, fieldName, maxSizeBytes, ALLOWED_REGISTRATION_EXTENSIONS,
                ALLOWED_REGISTRATION_MIME_TYPES, ALLOWED_REGISTRATION_TYPES_DISPLAY);
    }

    private static void validateWithRules(MultipartFile file, String fieldName, long maxSizeBytes,
                                          Set<String> allowedExtensions, Set<String> allowedMimes,
                                          String typesDisplay) {
        boolean isId = isIndonesian();

        if (file == null || file.isEmpty()) {
            String msg = isId ? "File kosong atau tidak disediakan." : "File is empty or not provided.";
            throw new FileValidationException(fieldName, msg);
        }

        // 1. Validate File Size (10 MB limit)
        if (file.getSize() > maxSizeBytes) {
            long maxMb = maxSizeBytes / (1024 * 1024);
            String msg = isId
                    ? "Ukuran file melebihi batas maksimum " + maxMb + " MB."
                    : "File size exceeds the maximum allowed limit of " + maxMb + " MB.";
            throw new FileValidationException(fieldName, msg);
        }

        // 2. Validate Extension
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            String msg = isId
                    ? "Nama file tidak valid. File harus memiliki ekstensi."
                    : "Invalid file name. The file must have an extension.";
            throw new FileValidationException(fieldName, msg);
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        if (!allowedExtensions.contains(extension)) {
            String msg = isId
                    ? "Tipe file tidak diizinkan. Tipe yang diizinkan: " + typesDisplay + "."
                    : "File type not allowed. Allowed types are: " + typesDisplay + ".";
            throw new FileValidationException(fieldName, msg);
        }

        // 3. Inspect REAL File Content / Magic Bytes via Apache Tika
        String detectedMimeType;
        try (InputStream is = file.getInputStream()) {
            detectedMimeType = TIKA.detect(is, originalFilename);
        } catch (IOException ex) {
            log.error("Failed to inspect file stream for {}: {}", originalFilename, ex.getMessage(), ex);
            String msg = isId
                    ? "Gagal membaca konten file."
                    : "Unable to inspect file contents.";
            throw new FileValidationException(fieldName, msg);
        }

        if (detectedMimeType == null || !allowedMimes.contains(detectedMimeType.toLowerCase())) {
            log.warn("File rejected by magic-byte detection. File: {}, Extension: {}, Detected MIME: {}",
                    originalFilename, extension, detectedMimeType);
            String msg = isId
                    ? "Konten file tidak sesuai dengan format yang diizinkan. Terdeteksi: " + detectedMimeType + ". Format yang diizinkan: " + typesDisplay + "."
                    : "File content does not match allowed types. Detected: " + detectedMimeType + ". Allowed types: " + typesDisplay + ".";
            throw new FileValidationException(fieldName, msg);
        }

        // 4. If DOCX or XLSX, verify that the file is genuine OOXML and does not contain VBA macros
        if ("docx".equals(extension) || "xlsx".equals(extension)) {
            validateOpenXmlStructure(file, fieldName, extension, isId);
        }
    }

    private static void validateOpenXmlStructure(MultipartFile file, String fieldName, String extension, boolean isId) {
        boolean hasContentTypes = false;
        boolean hasDocumentFolder = false;
        String requiredPrefix = "xlsx".equals(extension) ? "xl/" : "word/";

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if ("[Content_Types].xml".equalsIgnoreCase(entryName)) {
                    hasContentTypes = true;
                }
                if (entryName.toLowerCase().startsWith(requiredPrefix)) {
                    hasDocumentFolder = true;
                }
                if (entryName.toLowerCase().contains("vbaproject.bin")
                        || entryName.toLowerCase().contains("vba")
                        || entryName.toLowerCase().endsWith(".bin")
                        || entryName.toLowerCase().contains("macros")) {
                    String msg = isId
                            ? "File dengan makro (.xlsm/.docm) tidak diizinkan demi alasan keamanan."
                            : "Macro-enabled files (.xlsm/.docm) are blocked for security reasons.";
                    throw new FileValidationException(fieldName, msg);
                }
                zis.closeEntry();
            }
        } catch (FileValidationException e) {
            throw e;
        } catch (Exception ex) {
            String msg = isId
                    ? "File bukan dokumen Office Open XML (" + extension.toUpperCase() + ") yang valid."
                    : "The file is not a valid Office Open XML (" + extension.toUpperCase() + ") document.";
            throw new FileValidationException(fieldName, msg);
        }

        if (!hasContentTypes || !hasDocumentFolder) {
            String msg = isId
                    ? "File bukan dokumen Office Open XML (" + extension.toUpperCase() + ") yang valid (arsip zip tidak sesuai)."
                    : "The file is not a valid Office Open XML (" + extension.toUpperCase() + ") document (invalid package structure).";
            throw new FileValidationException(fieldName, msg);
        }
    }

    private static boolean isIndonesian() {
        Locale locale = LocaleContextHolder.getLocale();
        return locale != null && "id".equalsIgnoreCase(locale.getLanguage());
    }
}
