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

public class FileValidationUtil {

    private static final Logger log = LoggerFactory.getLogger(FileValidationUtil.class);
    private static final Tika TIKA = new Tika();

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/pjpeg"
    );
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
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            String msg = isId
                    ? "Tipe file tidak diizinkan. Tipe yang diizinkan: PDF, PNG, JPG, JPEG."
                    : "File type not allowed. Allowed types are: PDF, PNG, JPG, JPEG.";
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

        if (detectedMimeType == null || !ALLOWED_MIME_TYPES.contains(detectedMimeType.toLowerCase())) {
            log.warn("File rejected by magic-byte detection. File: {}, Extension: {}, Detected MIME: {}",
                    originalFilename, extension, detectedMimeType);
            String msg = isId
                    ? "Konten file tidak sesuai dengan format yang diizinkan. Terdeteksi: " + detectedMimeType + ". Format yang diizinkan: PDF, PNG, JPG, JPEG."
                    : "File content does not match allowed types. Detected: " + detectedMimeType + ". Allowed types: PDF, PNG, JPG, JPEG.";
            throw new FileValidationException(fieldName, msg);
        }
    }

    private static boolean isIndonesian() {
        Locale locale = LocaleContextHolder.getLocale();
        return locale != null && "id".equalsIgnoreCase(locale.getLanguage());
    }
}
