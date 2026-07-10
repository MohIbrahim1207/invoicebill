package com.billing.invoicehub.util;

import org.springframework.web.multipart.MultipartFile;
import java.util.Arrays;
import java.util.List;

public class FileValidationUtil {

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("pdf", "png", "jpg", "jpeg");
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList("application/pdf", "image/png", "image/jpeg", "image/pjpeg");
    private static final long MAX_FILE_SIZE_10MB = 10 * 1024 * 1024; // 10 MB

    public static void validateFile(MultipartFile file) {
        validateFile(file, MAX_FILE_SIZE_10MB);
    }

    public static void validateFile(MultipartFile file, long maxSizeBytes) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty or not provided.");
        }

        // Validate Size
        if (file.getSize() > maxSizeBytes) {
            long maxMb = maxSizeBytes / (1024 * 1024);
            throw new IllegalArgumentException("File size exceeds the maximum allowed limit of " + maxMb + " MB.");
        }

        // Validate Extension
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new IllegalArgumentException("Invalid file name. The file must have an extension.");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("File type not allowed. Allowed types are: PDF, PNG, JPG, JPEG.");
        }

        // Validate MIME type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("File content type is not supported.");
        }
    }
}
