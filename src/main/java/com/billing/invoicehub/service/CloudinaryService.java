package com.billing.invoicehub.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service for handling file uploads to Cloudinary cloud storage.
 * Replaces local FileStorageService for cloud-based persistence.
 * This service is only available when Cloudinary is properly configured.
 */
@Service
public class CloudinaryService {

    private static final Logger logger = LoggerFactory.getLogger(CloudinaryService.class);

    // Allowed file extensions for security
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "pdf", "png", "jpg", "jpeg", "webp", "doc", "docx", "xls", "xlsx"
    );

    // Max file size: 50MB
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /**
     * Upload invoice file to Cloudinary.
     *
     * @param file MultipartFile to upload
     * @return Secure URL of uploaded file
     * @throws IOException if upload fails
     * @throws IllegalArgumentException if file validation fails
     */
    public String uploadInvoiceFile(MultipartFile file) throws IOException {
        return uploadFile(file, "invoicehub/invoices");
    }

    /**
     * Upload supporting document to Cloudinary.
     *
     * @param file MultipartFile to upload
     * @return Secure URL of uploaded file
     * @throws IOException if upload fails
     * @throws IllegalArgumentException if file validation fails
     */
    public String uploadSupportingDocument(MultipartFile file) throws IOException {
        return uploadFile(file, "invoicehub/supporting-documents");
    }

    /**
     * Upload vendor document to Cloudinary.
     *
     * @param file MultipartFile to upload
     * @return Secure URL of uploaded file
     * @throws IOException if upload fails
     * @throws IllegalArgumentException if file validation fails
     */
    public String uploadVendorDocument(MultipartFile file) throws IOException {
        return uploadFile(file, "invoicehub/vendor-documents");
    }

    /**
     * Upload profile image to Cloudinary.
     *
     * @param file MultipartFile to upload
     * @return Secure URL of uploaded file
     * @throws IOException if upload fails
     * @throws IllegalArgumentException if file validation fails
     */
    public String uploadProfileImage(MultipartFile file) throws IOException {
        return uploadFile(file, "invoicehub/profile-images");
    }

    /**
     * Upload company logo to Cloudinary.
     *
     * @param file MultipartFile to upload
     * @return Secure URL of uploaded file
     * @throws IOException if upload fails
     * @throws IllegalArgumentException if file validation fails
     */
    public String uploadCompanyLogo(MultipartFile file) throws IOException {
        return uploadFile(file, "invoicehub/company-logos");
    }

    /**
     * Upload a generic vendor ticket document to Cloudinary.
     *
     * @param file MultipartFile to upload
     * @return Secure URL of uploaded file
     * @throws IOException if upload fails
     * @throws IllegalArgumentException if file validation fails
     */
    public String uploadDocument(MultipartFile file) throws IOException {
        return uploadFile(file, "invoicehub/vendor-ticket-documents");
    }

    /**
     * Generic file upload method with folder organization.
     *
     * @param file MultipartFile to upload
     * @param folder Target folder in Cloudinary
     * @return Secure URL of uploaded file
     * @throws IOException if upload fails
     * @throws IllegalArgumentException if file validation fails
     */
    private String uploadFile(MultipartFile file, String folder) throws IOException {
        // Validate file
        validateFile(file);

        try {
            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            String uniqueFilename = folder + "/" + UUID.randomUUID() + "." + fileExtension;

            // Upload to Cloudinary
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                    "public_id", uniqueFilename,
                    "resource_type", "auto",
                    "overwrite", false
                )
            );

            String secureUrl = (String) result.get("secure_url");
            logger.info("File uploaded successfully to Cloudinary: {}", uniqueFilename);

            return secureUrl;

        } catch (IOException e) {
            logger.error("Failed to upload file to Cloudinary: {}", e.getMessage(), e);
            throw new IOException("Failed to upload file to Cloudinary: " + e.getMessage(), e);
        }
    }

    /**
     * Validate file before upload.
     *
     * @param file MultipartFile to validate
     * @throws IllegalArgumentException if file is invalid
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required and cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                "File size exceeds maximum limit of 50MB. Current size: " +
                (file.getSize() / (1024 * 1024)) + "MB"
            );
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new IllegalArgumentException("File must have a valid extension");
        }

        String extension = getFileExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException(
                "Unsupported file type: " + extension +
                ". Allowed types: " + String.join(", ", ALLOWED_EXTENSIONS)
            );
        }
    }

    /**
     * Extract file extension from filename.
     *
     * @param filename Original filename
     * @return File extension without dot
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * Delete file from Cloudinary.
     *
     * @param fileUrl Secure URL of file to delete
     * @return true if deletion was successful, false otherwise
     */
    public boolean deleteFile(String fileUrl) {
        try {
            if (fileUrl == null || !fileUrl.startsWith("http")) {
                return false;
            }

            // Extract public_id from URL
            String publicId = extractPublicIdFromUrl(fileUrl);
            if (publicId == null) {
                return false;
            }

            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            logger.info("File deleted successfully from Cloudinary: {}", publicId);
            return true;

        } catch (Exception e) {
            logger.warn("Failed to delete file from Cloudinary: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract public_id from Cloudinary secure URL.
     *
     * @param secureUrl Cloudinary secure URL
     * @return Public ID or null if unable to extract
     */
    public String extractPublicIdFromUrl(String secureUrl) {
        try {
            String[] parts = secureUrl.split("/upload/");
            if (parts.length != 2) {
                return null;
            }

            String pathPart = parts[1];
            pathPart = pathPart.replaceAll("^v\\d+/", "");
            return pathPart;
        } catch (Exception e) {
            logger.warn("Failed to extract public_id from URL: {}", secureUrl);
            return null;
        }
    }
}





