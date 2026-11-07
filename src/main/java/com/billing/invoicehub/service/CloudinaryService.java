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

import org.springframework.context.annotation.Conditional;
import com.billing.invoicehub.config.CloudinaryEnvironmentCondition;

@Service
@Conditional(CloudinaryEnvironmentCondition.class)
public class CloudinaryService {

    private static final Logger logger = LoggerFactory.getLogger(CloudinaryService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "webp", "doc", "docx", "xls", "xlsx");

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

    public String uploadQuotePdf(byte[] pdfBytes, String fileName) throws IOException {
        try {
            String uniqueFilename = "invoicehub/generated-quotes/" + UUID.randomUUID() + "_"
                    + fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) cloudinary.uploader().upload(
                    pdfBytes,
                    ObjectUtils.asMap(
                            "public_id", uniqueFilename,
                            "resource_type", "auto",
                            "type", "authenticated",
                            "overwrite", false));
            return (String) result.get("secure_url");
        } catch (IOException e) {
            logger.error("Failed to upload quote PDF to Cloudinary: {}", e.getMessage(), e);
            throw new IOException("Failed to upload quote PDF to Cloudinary: " + e.getMessage(), e);
        }
    }

    public String uploadQuoteDocx(byte[] docxBytes, String fileName) throws IOException {
        try {
            String uniqueFilename = "invoicehub/generated-quotes/" + UUID.randomUUID() + "_"
                    + fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) cloudinary.uploader().upload(
                    docxBytes,
                    ObjectUtils.asMap(
                            "public_id", uniqueFilename,
                            "resource_type", "auto",
                            "type", "authenticated",
                            "overwrite", false));
            return (String) result.get("secure_url");
        } catch (IOException e) {
            logger.error("Failed to upload quote DOCX to Cloudinary: {}", e.getMessage(), e);
            throw new IOException("Failed to upload quote DOCX to Cloudinary: " + e.getMessage(), e);
        }
    }

    private String uploadFile(MultipartFile file, String folder) throws IOException {
        validateFile(file);

        try {
            String uniqueFilename = folder + "/" + UUID.randomUUID();

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "public_id", uniqueFilename,
                            "resource_type", "auto",
                            "type", "authenticated",
                            "overwrite", false));

            String secureUrl = (String) result.get("secure_url");

            logger.info("File uploaded successfully to Cloudinary (authenticated): {}", uniqueFilename);

            return secureUrl;

        } catch (IOException e) {
            logger.error("Failed to upload file to Cloudinary: {}", e.getMessage(), e);
            throw new IOException("Failed to upload file to Cloudinary: " + e.getMessage(), e);
        }
    }

    /**
     * In v1.6.3: Simply returns the stored URL directly for all files.
     * Authenticated files uploaded to Cloudinary already contain signature tokens in the URL.
     * Does not call privateDownload to prevent 'Resource not found' errors on PDFs/images.
     */
    public String generateSignedDownloadUrl(String fileUrl, int expirySeconds) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return fileUrl;
        }
        return fileUrl.trim();
    }

    public String extractPublicId(String url) {
        if (url == null) return null;
        int idx = url.indexOf("/authenticated/");
        if (idx == -1) {
            idx = url.indexOf("/upload/");
            if (idx == -1) return null;
            idx += "/upload/".length();
        } else {
            idx += "/authenticated/".length();
        }
        String path = url.substring(idx);
        // Strip signature if present (e.g. s--abc123--/)
        path = path.replaceAll("^s--[^/]+--/", "");
        // Strip version prefix if present (e.g. v1234567890/)
        path = path.replaceAll("^v\\d+/", "");
        return path;
    }

    public String extractResourceType(String url) {
        if (url == null) return "image";
        if (url.contains("/raw/")) return "raw";
        if (url.contains("/video/")) return "video";
        return "image";
    }

    private void validateFile(MultipartFile file) {
        com.billing.invoicehub.util.FileValidationUtil.validateFile(file);
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    public boolean deleteFile(String fileUrl) {
        try {
            if (fileUrl == null || !fileUrl.startsWith("http")) {
                return false;
            }

            String publicId = extractPublicId(fileUrl);
            if (publicId == null) {
                return false;
            }

            String type = fileUrl.contains("/authenticated/") ? "authenticated" : "upload";
            String resourceType = extractResourceType(fileUrl);
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("type", type, "resource_type", resourceType));

            logger.info("File deleted successfully from Cloudinary: {}", publicId);
            return true;

        } catch (Exception e) {
            logger.warn("Failed to delete file from Cloudinary: {}", e.getMessage());
            return false;
        }
    }

    public String extractPublicIdFromUrl(String secureUrl) {
        return extractPublicId(secureUrl);
    }
}
