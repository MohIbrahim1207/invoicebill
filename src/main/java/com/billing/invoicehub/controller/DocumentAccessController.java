package com.billing.invoicehub.controller;

import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.service.CloudinaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.Optional;

@Controller
@RequestMapping("/documents")
public class DocumentAccessController {

    private static final Logger log = LoggerFactory.getLogger(DocumentAccessController.class);

    private final ObjectProvider<CloudinaryService> cloudinaryServiceProvider;
    private final AppUserRepository appUserRepository;
    private final JdbcTemplate jdbc;

    public DocumentAccessController(ObjectProvider<CloudinaryService> cloudinaryServiceProvider,
                                    AppUserRepository appUserRepository,
                                    JdbcTemplate jdbc) {
        this.cloudinaryServiceProvider = cloudinaryServiceProvider;
        this.appUserRepository = appUserRepository;
        this.jdbc = jdbc;
    }

    @GetMapping("/view")
    public String viewDocument(@RequestParam("url") String targetUrl, Authentication authentication) {
        if (targetUrl == null || targetUrl.isBlank()) {
            throw new IllegalArgumentException("Document URL must not be empty.");
        }

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required to access documents.");
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        boolean isStaff = authorities.stream().anyMatch(a ->
                "ROLE_ADMIN".equals(a.getAuthority())
                        || "ROLE_SUPER_ADMIN".equals(a.getAuthority())
                        || "ROLE_ACCOUNTS".equals(a.getAuthority()));

        if (!isStaff) {
            // Vendor access verification: verify that this document belongs to the authenticated vendor
            String username = authentication.getName();
            Optional<AppUser> userOpt = appUserRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                log.warn("Document access denied: User '{}' not found in database.", username);
                throw new AccessDeniedException("User not found.");
            }

            AppUser user = userOpt.get();
            boolean authorized = isVendorDocumentOwner(user, targetUrl.trim());
            if (!authorized) {
                log.warn("SECURITY ALERT: Vendor '{}' (id={}) attempted unauthorized access to document: '{}'",
                        username, user.getId(), targetUrl);
                throw new AccessDeniedException("Access denied: You do not have permission to view this document.");
            }
        }

        // Authorization succeeded!
        CloudinaryService cloudinaryService = cloudinaryServiceProvider.getIfAvailable();
        if (cloudinaryService != null) {
            // Generates signed URL valid for 15 minutes (900 seconds)
            // Or returns legacy public upload URL directly for backward compatibility
            String signedUrl = cloudinaryService.generateSignedDownloadUrl(targetUrl.trim(), 900);
            return "redirect:" + signedUrl;
        }

        // Fallback if Cloudinary bean is not active (e.g. mock/test)
        return "redirect:" + targetUrl.trim();
    }

    private boolean isVendorDocumentOwner(AppUser user, String url) {
        Long userId = user.getId();

        // 1. Check user profile documents
        if (url.equalsIgnoreCase(user.getGstDocumentUrl())
                || url.equalsIgnoreCase(user.getCompanyDocumentUrl())
                || url.equalsIgnoreCase(user.getSupportingDocumentUrl())
                || url.equalsIgnoreCase(user.getProfileImageUrl())
                || url.equalsIgnoreCase(user.getCompanyLogoUrl())) {
            return true;
        }

        // 2. Check vendor tickets owned by this vendor
        try {
            Integer ticketMatchCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM vendor_ticket " +
                "WHERE (owner_id = ? OR vendor_id = ?) " +
                "AND (LOWER(invoice_file_url) = LOWER(?) " +
                "  OR LOWER(tax_document_url) = LOWER(?) " +
                "  OR LOWER(po_copy_url) = LOWER(?) " +
                "  OR LOWER(delivery_note_url) = LOWER(?) " +
                "  OR LOWER(other_document_url) = LOWER(?) " +
                "  OR LOWER(supporting_document_url) = LOWER(?))",
                Integer.class,
                userId, userId, url, url, url, url, url, url
            );
            if (ticketMatchCount != null && ticketMatchCount > 0) {
                return true;
            }
        } catch (Exception ex) {
            log.warn("Error querying ticket ownership for document: {}", ex.getMessage());
        }

        // 3. Check billing invoices owned by this vendor
        try {
            Integer invoiceMatchCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM invoice i " +
                "LEFT JOIN client c ON i.client_id = c.id " +
                "LEFT JOIN vendor_ticket vt ON i.vendor_ticket_id = vt.id " +
                "WHERE (c.owner_id = ? OR vt.owner_id = ? OR vt.vendor_id = ?) " +
                "AND LOWER(i.file_url) = LOWER(?)",
                Integer.class,
                userId, userId, userId, url
            );
            if (invoiceMatchCount != null && invoiceMatchCount > 0) {
                return true;
            }
        } catch (Exception ex) {
            log.warn("Error querying invoice ownership for document: {}", ex.getMessage());
        }

        return false;
    }
}
