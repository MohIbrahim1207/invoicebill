package com.billing.invoicehub.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CloudinaryService {

    private static final Logger logger = LoggerFactory.getLogger(CloudinaryService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "webp", "doc", "docx", "xls", "xlsx"
    );

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public String uploadInvoiceFile(MultipartFile file) throws IOException {
        return uploadFile(file, "invoicehub/invoices");
    }

    public String uploadSupportingDocument(MultipartFile file) throws IOException {
        return uploadFile(file, "invoicehub/supporting-documents");
    }

    public String uploadVendorDocument(MultipartFile file) throws IOException {
        return uploadFile(file, "invoicehub/vendor-documents");
    }

    public String uploadProfileImage(MultipartFile file) throws IOException {
        return uploadFile(file, "invoicehub/profile-images");
    }

    public String uploadCompanyLogo(MultipartFile file) throws IOException {
        return uploadFile(file, "invoicehub/company-logos");
    }

    public String uploadDocument(MultipartFile file) throws IOException {
        return uploadFile(file, "invoicehub/vendor-ticket-documents");
    }

    private String uploadFile(MultipartFile file, String folder) throws IOException {
        validateFile(file);

        try {
            // No extension in public_id — Cloudinary handles it automatically
            String uniqueFilename = folder + "/" + UUID.randomUUID();

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "public_id", uniqueFilename,
                            "resource_type", "raw",
                            "overwrite", false
                    )
            );

            String secureUrl = (String) result.get("secure_url");
            secureUrl = fixDocumentUrl(secureUrl, file.getOriginalFilename());
            logger.info("File uploaded successfully to Cloudinary: {}", uniqueFilename);
            return secureUrl;

        } catch (IOException e) {
            logger.error("Failed to upload file to Cloudinary: {}", e.getMessage(), e);
            throw new IOException("Failed to upload file to Cloudinary: " + e.getMessage(), e);
        }
    }

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

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private String fixDocumentUrl(String secureUrl, String originalFilename) {
        if (secureUrl == null || originalFilename == null) {
            return secureUrl;
        }

        String extension = getFileExtension(originalFilename);
        if ("pdf".equals(extension) || "doc".equals(extension) || "docx".equals(extension)) {
            return secureUrl.replace("/image/upload/", "/raw/upload/");
        }

        return secureUrl;
    }

    public boolean deleteFile(String fileUrl) {
        try {
            if (fileUrl == null || !fileUrl.startsWith("http")) {
                return false;
            }
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

    public String extractPublicIdFromUrl(String secureUrl) {
        try {
            String[] parts = secureUrl.split("/upload/");
            if (parts.length != 2) return null;

            String pathPart = parts[1];
            pathPart = pathPart.replaceAll("^v\\d+/", "");
            return pathPart;
        } catch (Exception e) {
            logger.warn("Failed to extract public_id from URL: {}", secureUrl);
            return null;
        }
    }
}