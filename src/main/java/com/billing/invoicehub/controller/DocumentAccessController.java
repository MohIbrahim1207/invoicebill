package com.billing.invoicehub.controller;

import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.Invoice;
import com.billing.invoicehub.entity.VendorTicket;
import com.billing.invoicehub.entity.AiQuoteConversion;
import com.billing.invoicehub.entity.AiQuoteSession;
import com.billing.invoicehub.exception.ResourceNotFoundException;
import com.billing.invoicehub.repository.AiQuoteConversionRepository;
import com.billing.invoicehub.repository.AiQuoteSessionRepository;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.InvoiceRepository;
import com.billing.invoicehub.repository.VendorTicketRepository;
import com.billing.invoicehub.service.CloudinaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Collection;
import java.util.Optional;

@Controller
@RequestMapping("/documents")
public class DocumentAccessController {

    private static final Logger log = LoggerFactory.getLogger(DocumentAccessController.class);

    private final ObjectProvider<CloudinaryService> cloudinaryServiceProvider;
    private final AppUserRepository appUserRepository;
    private final VendorTicketRepository vendorTicketRepository;
    private final InvoiceRepository invoiceRepository;
    private final ObjectProvider<AiQuoteConversionRepository> aiQuoteConversionRepositoryProvider;
    private final ObjectProvider<AiQuoteSessionRepository> aiQuoteSessionRepositoryProvider;

    public DocumentAccessController(ObjectProvider<CloudinaryService> cloudinaryServiceProvider,
                                    AppUserRepository appUserRepository,
                                    VendorTicketRepository vendorTicketRepository,
                                    InvoiceRepository invoiceRepository,
                                    ObjectProvider<AiQuoteConversionRepository> aiQuoteConversionRepositoryProvider,
                                    ObjectProvider<AiQuoteSessionRepository> aiQuoteSessionRepositoryProvider) {
        this.cloudinaryServiceProvider = cloudinaryServiceProvider;
        this.appUserRepository = appUserRepository;
        this.vendorTicketRepository = vendorTicketRepository;
        this.invoiceRepository = invoiceRepository;
        this.aiQuoteConversionRepositoryProvider = aiQuoteConversionRepositoryProvider;
        this.aiQuoteSessionRepositoryProvider = aiQuoteSessionRepositoryProvider;
    }

    /**
     * ID-based ticket document access: /documents/ticket/{id}/{field}
     */
    @GetMapping("/ticket/{id}/{field}")
    public String viewTicketDocument(@PathVariable("id") Long id,
                                     @PathVariable("field") String field,
                                     Authentication authentication) {
        checkAuthenticated(authentication);
        VendorTicket ticket = vendorTicketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + id));

        if (!isStaff(authentication)) {
            AppUser currentUser = requireAuthenticatedUser(authentication);
            boolean isOwner = (ticket.getOwner() != null && ticket.getOwner().getId().equals(currentUser.getId()))
                    || (ticket.getVendor() != null && ticket.getVendor().getId().equals(currentUser.getId()));
            if (!isOwner) {
                log.warn("SECURITY ALERT: User '{}' (id={}) attempted unauthorized access to ticket {} document",
                        currentUser.getUsername(), currentUser.getId(), id);
                throw new AccessDeniedException("Access denied: You do not have permission to view documents for this ticket.");
            }
        }

        String rawUrl = resolveTicketDocumentUrl(ticket, field);
        if (rawUrl == null || rawUrl.isBlank()) {
            return "document-not-available";
        }

        return redirectToSecuredUrl(rawUrl);
    }

    /**
     * ID-based vendor profile document access: /documents/vendor/{id}/{field}
     */
    @GetMapping("/vendor/{id}/{field}")
    public String viewVendorDocument(@PathVariable("id") Long id,
                                     @PathVariable("field") String field,
                                     Authentication authentication) {
        checkAuthenticated(authentication);
        AppUser vendor = appUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found with id: " + id));

        if (!isStaff(authentication)) {
            AppUser currentUser = requireAuthenticatedUser(authentication);
            if (!vendor.getId().equals(currentUser.getId())) {
                log.warn("SECURITY ALERT: User '{}' (id={}) attempted unauthorized access to vendor {} profile document",
                        currentUser.getUsername(), currentUser.getId(), id);
                throw new AccessDeniedException("Access denied: You do not have permission to view documents for this vendor.");
            }
        }

        String rawUrl = resolveVendorDocumentUrl(vendor, field);
        if (rawUrl == null || rawUrl.isBlank()) {
            return "document-not-available";
        }

        return redirectToSecuredUrl(rawUrl);
    }

    /**
     * ID-based invoice document access: /documents/invoice/{id}
     */
    @GetMapping("/invoice/{id}")
    public String viewInvoiceDocument(@PathVariable("id") Long id,
                                      Authentication authentication) {
        checkAuthenticated(authentication);
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));

        if (!isStaff(authentication)) {
            AppUser currentUser = requireAuthenticatedUser(authentication);
            boolean isClientOwner = invoice.getClient() != null
                    && invoice.getClient().getOwner() != null
                    && invoice.getClient().getOwner().getId().equals(currentUser.getId());

            boolean isLinkedTicketOwner = vendorTicketRepository.findAllByInvoice_Id(id).stream()
                    .anyMatch(vt -> (vt.getOwner() != null && vt.getOwner().getId().equals(currentUser.getId()))
                            || (vt.getVendor() != null && vt.getVendor().getId().equals(currentUser.getId())));

            if (!isClientOwner && !isLinkedTicketOwner) {
                log.warn("SECURITY ALERT: User '{}' (id={}) attempted unauthorized access to invoice {}",
                        currentUser.getUsername(), currentUser.getId(), id);
                throw new AccessDeniedException("Access denied: You do not have permission to view this invoice document.");
            }
        }

        String rawUrl = invoice.getFileUrl();
        if (rawUrl == null || rawUrl.isBlank()) {
            return "document-not-available";
        }

        return redirectToSecuredUrl(rawUrl);
    }

    /**
     * ID-based AI quote document access: /documents/ai-quote/{id}/{field} (Staff only)
     */
    @GetMapping("/ai-quote/{id}/{field}")
    public String viewAiQuoteDocument(@PathVariable("id") Long id,
                                      @PathVariable("field") String field,
                                      Authentication authentication) {
        checkAuthenticated(authentication);
        if (!isStaff(authentication)) {
            throw new AccessDeniedException("Access denied: Only internal staff may access AI quote documents.");
        }

        AiQuoteConversionRepository repo = aiQuoteConversionRepositoryProvider.getIfAvailable();
        if (repo == null) {
            return "document-not-available";
        }

        AiQuoteConversion conversion = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AI quote conversion not found with id: " + id));

        String rawUrl = switch (field.toLowerCase()) {
            case "original" -> conversion.getOriginalFileUrl();
            case "generated-pdf", "pdf" -> conversion.getGeneratedQuoteFileUrl();
            case "generated-docx", "docx" -> conversion.getGeneratedDocxFileUrl();
            default -> null;
        };

        if (rawUrl == null || rawUrl.isBlank()) {
            return "document-not-available";
        }

        return redirectToSecuredUrl(rawUrl);
    }

    /**
     * ID-based AI quote session document access: /documents/session/{id}/{field}
     */
    @GetMapping({"/session/{id}/{field}", "/ai-quote-session/{id}/{field}"})
    public String viewSessionDocument(@PathVariable("id") Long id,
                                      @PathVariable("field") String field,
                                      Authentication authentication) {
        checkAuthenticated(authentication);
        if (!isStaff(authentication)) {
            throw new AccessDeniedException("Access denied: Only internal staff may access AI quotation session documents.");
        }

        AiQuoteSessionRepository repo = aiQuoteSessionRepositoryProvider.getIfAvailable();
        if (repo == null) {
            return "document-not-available";
        }

        AiQuoteSession session = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AI quote session not found with id: " + id));

        String rawUrl = switch (field.toLowerCase()) {
            case "generated-pdf", "pdf" -> session.getGeneratedPdfUrl();
            case "generated-docx", "docx" -> session.getGeneratedDocxUrl();
            default -> null;
        };

        if (rawUrl == null || rawUrl.isBlank()) {
            return "document-not-available";
        }

        return redirectToSecuredUrl(rawUrl);
    }

    @GetMapping("/not-available")
    public String documentNotAvailable() {
        return "document-not-available";
    }

    // ─── Private Helpers ─────────────────────────────────────────────────────────

    private void checkAuthenticated(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required to access documents.");
        }
    }

    private AppUser requireAuthenticatedUser(Authentication authentication) {
        checkAuthenticated(authentication);
        String username = authentication.getName();
        return appUserRepository.findByUsername(username)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found in database."));
    }

    private boolean isStaff(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        return authorities.stream().anyMatch(a ->
                "ROLE_ADMIN".equals(a.getAuthority())
                        || "ROLE_SUPER_ADMIN".equals(a.getAuthority())
                        || "ROLE_ACCOUNTS".equals(a.getAuthority()));
    }

    private String resolveTicketDocumentUrl(VendorTicket ticket, String field) {
        return switch (field.toLowerCase()) {
            case "invoice", "invoice-file" -> ticket.getInvoiceFileUrl();
            case "tax", "tax-document" -> ticket.getTaxDocumentUrl();
            case "po", "po-copy" -> ticket.getPoCopyUrl();
            case "delivery", "delivery-note" -> ticket.getDeliveryNoteUrl();
            case "other", "other-document" -> ticket.getOtherDocumentUrl();
            case "supporting", "supporting-document" -> ticket.getSupportingDocumentUrl();
            default -> null;
        };
    }

    private String resolveVendorDocumentUrl(AppUser vendor, String field) {
        return switch (field.toLowerCase()) {
            case "gst", "gst-document" -> vendor.getGstDocumentUrl();
            case "company", "company-document" -> vendor.getCompanyDocumentUrl();
            case "supporting", "supporting-document" -> vendor.getSupportingDocumentUrl();
            case "profile", "profile-image" -> vendor.getProfileImageUrl();
            case "logo", "company-logo" -> vendor.getCompanyLogoUrl();
            default -> null;
        };
    }

    private String redirectToSecuredUrl(String targetUrl) {
        // v1.6.3: Simply redirect to the stored URL from the database for ALL files.
        // Cloudinary authenticated URLs already contain embedded signature segments.
        return "redirect:" + targetUrl.trim();
    }
}
