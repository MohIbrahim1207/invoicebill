package com.billing.invoicehub.controller;

import com.billing.invoicehub.dto.VendorTicketWizardState;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.TicketStatus;
import com.billing.invoicehub.entity.VendorTicket;
import com.billing.invoicehub.service.FileStorageService;
import com.billing.invoicehub.service.VendorTicketService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Controller
public class VendorTicketController {
    private static final Logger logger = LoggerFactory.getLogger(VendorTicketController.class);
    private static final String WIZARD_SESSION_KEY = "vendorTicketWizardState";
    private static final String SUCCESS_TICKET_NO_KEY = "vendorTicketSuccessTicketNo";

    private final VendorTicketService vendorTicketService;

    @Autowired(required = false)
    private FileStorageService fileStorageService;

    public VendorTicketController(VendorTicketService vendorTicketService) {
        this.vendorTicketService = vendorTicketService;
    }

    @GetMapping(value = {"/vendor-tickets"})
    public String vendorTickets(@RequestParam(value="ticketNo", required=false) String ticketNo, @RequestParam(value="invoiceNo", required=false) String invoiceNo, @RequestParam(value="year", required=false) Integer year, @RequestParam(value="status", required=false, defaultValue="ALL") String status, Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = this.currentUser(authentication);
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/login";
        }

        boolean admin = this.isAdmin(currentUser.get());
        List<VendorTicket> tickets = admin
                ? this.vendorTicketService.searchTickets(ticketNo, invoiceNo, year, status)
                : this.vendorTicketService.searchTicketsForOwner(currentUser.get().getId(), ticketNo, invoiceNo, year, status);

        model.addAttribute("tickets", tickets);
        model.addAttribute("availableYears", admin
                ? this.vendorTicketService.availableYears()
                : this.vendorTicketService.availableYearsForOwner(currentUser.get().getId()));
        model.addAttribute("statusOptions", TicketStatus.values());
        model.addAttribute("ticketNo", ticketNo);
        model.addAttribute("invoiceNo", invoiceNo);
        model.addAttribute("year", year);
        model.addAttribute("status", status == null ? "ALL" : status);
        return "vendor-tickets";
    }

    @PostMapping(value = {"/vendor-tickets/{id}/cancel"})
    public String cancelTicket(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = this.currentUser(authentication);
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/login";
        }

        boolean cancelled = this.vendorTicketService.cancelTicket(id, authentication.getName());
        if (cancelled) {
            redirectAttributes.addFlashAttribute("message", "Ticket cancelled successfully.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Ticket not found or access denied.");
        }
        return "redirect:/vendor-tickets";
    }

    @GetMapping(value = {"/vendor-tickets/{id}/history"})
    public String ticketHistory(@PathVariable Long id, Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = this.currentUser(authentication);
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/login";
        }

        Optional<VendorTicket> ticket = this.vendorTicketService.getAccessibleTicket(id, authentication.getName());
        if (ticket.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Ticket not found or access denied.");
            return "redirect:/vendor-tickets";
        }

        List<?> history = this.vendorTicketService.getTicketHistory(id);
        model.addAttribute("ticket", ticket.get());
        model.addAttribute("history", history);
        return "ticket-history";
    }

    @GetMapping(value = {"/vendor-tickets/new"})
    public String newTicketStep2(Authentication authentication, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = this.currentUser(authentication);
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/login";
        }

        VendorTicketWizardState wizardState = this.wizardState(session);
        if (wizardState == null) {
            wizardState = new VendorTicketWizardState();
        }

        wizardState.setClientId(null);
        wizardState.setClientName(currentUser.get().getUsername());
        model.addAttribute("vendor", currentUser.get());
        model.addAttribute("wizardState", wizardState);
        return "vendor-ticket-new-step2";
    }

    @PostMapping(value = {"/vendor-tickets/new/step3"})
    public String saveStep2(@ModelAttribute VendorTicketWizardState wizardState, Authentication authentication, HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = this.currentUser(authentication);
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/login";
        }

        wizardState.setClientName(currentUser.get().getUsername());
        session.setAttribute(WIZARD_SESSION_KEY, wizardState);
        return "redirect:/vendor-tickets/new/step3";
    }

    @GetMapping(value = {"/vendor-tickets/new/step3"})
    public String newTicketStep3(Authentication authentication, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = this.currentUser(authentication);
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/login";
        }

        VendorTicketWizardState wizardState = this.wizardState(session);
        if (wizardState == null) {
            redirectAttributes.addFlashAttribute("error", "Please complete step 2 first.");
            return "redirect:/vendor-tickets/new";
        }

        model.addAttribute("vendor", currentUser.get());
        model.addAttribute("wizardState", wizardState);
        return "vendor-ticket-new-step3";
    }

    @PostMapping(value = {"/vendor-tickets/new/step4"})
    public String saveStep3(@RequestParam(value="invoiceFile") MultipartFile invoiceFile,
                           @RequestParam(value="documentFile", required=false) MultipartFile documentFile,
                           @RequestParam(value="taxDocument", required=false) MultipartFile taxDocument,
                           @RequestParam(value="poCopy", required=false) MultipartFile poCopy,
                           @RequestParam(value="deliveryNote", required=false) MultipartFile deliveryNote,
                           @RequestParam(value="otherDocument", required=false) MultipartFile otherDocument,
                           @RequestParam(value="supportingDocument", required=false) MultipartFile supportingDocument,
                           Authentication authentication, HttpSession session,
                           RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = this.currentUser(authentication);
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/login";
        }

        VendorTicketWizardState wizardState = this.wizardState(session);
        if (wizardState == null) {
            redirectAttributes.addFlashAttribute("error", "Please complete step 2 first.");
            return "redirect:/vendor-tickets/new";
        }
        if (invoiceFile == null || invoiceFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please upload the invoice file.");
            return "redirect:/vendor-tickets/new/step3";
        }

        if (fileStorageService == null) {
            redirectAttributes.addFlashAttribute("error", "File upload service is not configured. Please configure Firebase credentials.");
            return "redirect:/vendor-tickets/new/step3";
        }

        try {
            String invoiceFileUrl = fileStorageService.storeInvoiceFile(invoiceFile);
            wizardState.setInvoiceFileOriginalName(this.cleanFilename(invoiceFile.getOriginalFilename()));
            wizardState.setInvoiceFileUrl(invoiceFileUrl);

            if (documentFile != null && !documentFile.isEmpty()) {
                String documentUrl = fileStorageService.storeDocument(documentFile);
                wizardState.setDocumentUrl(documentUrl);
                wizardState.setDocumentPublicId(fileStorageService.extractObjectPathFromUrl(documentUrl));
            } else {
                wizardState.setDocumentUrl(null);
                wizardState.setDocumentPublicId(null);
            }

            if (wizardState.getTicketNo() == null) {
                wizardState.setTicketNo(this.vendorTicketService.generateTicketNo());
            }

            // Upload optional tax document
            if (taxDocument != null && !taxDocument.isEmpty()) {
                try {
                    String taxDocumentUrl = fileStorageService.storeVendorDocument(taxDocument);
                    wizardState.setTaxDocumentOriginalName(this.cleanFilename(taxDocument.getOriginalFilename()));
                    wizardState.setTaxDocumentUrl(taxDocumentUrl);
                } catch (IOException e) {
                    logger.warn("Failed to upload tax document: {}", e.getMessage());
                    redirectAttributes.addFlashAttribute("warning", "Tax document upload failed, but you can continue.");
                }
            } else {
                wizardState.setTaxDocumentOriginalName(null);
                wizardState.setTaxDocumentUrl(null);
            }

            // Upload optional PO copy
            if (poCopy != null && !poCopy.isEmpty()) {
                try {
                    String poCopyUrl = fileStorageService.storeVendorDocument(poCopy);
                    wizardState.setPoCopyOriginalName(this.cleanFilename(poCopy.getOriginalFilename()));
                    wizardState.setPoCopyUrl(poCopyUrl);
                } catch (IOException e) {
                    logger.warn("Failed to upload PO copy: {}", e.getMessage());
                }
            } else {
                wizardState.setPoCopyOriginalName(null);
                wizardState.setPoCopyUrl(null);
            }

            // Upload optional delivery note
            if (deliveryNote != null && !deliveryNote.isEmpty()) {
                try {
                    String deliveryNoteUrl = fileStorageService.storeVendorDocument(deliveryNote);
                    wizardState.setDeliveryNoteOriginalName(this.cleanFilename(deliveryNote.getOriginalFilename()));
                    wizardState.setDeliveryNoteUrl(deliveryNoteUrl);
                } catch (IOException e) {
                    logger.warn("Failed to upload delivery note: {}", e.getMessage());
                }
            } else {
                wizardState.setDeliveryNoteOriginalName(null);
                wizardState.setDeliveryNoteUrl(null);
            }

            // Upload optional other document
            if (otherDocument != null && !otherDocument.isEmpty()) {
                try {
                    String otherDocumentUrl = fileStorageService.storeVendorDocument(otherDocument);
                    wizardState.setOtherDocumentOriginalName(this.cleanFilename(otherDocument.getOriginalFilename()));
                    wizardState.setOtherDocumentUrl(otherDocumentUrl);
                } catch (IOException e) {
                    logger.warn("Failed to upload other document: {}", e.getMessage());
                }
            } else {
                wizardState.setOtherDocumentOriginalName(null);
                wizardState.setOtherDocumentUrl(null);
            }

            // Upload supporting document
            if (supportingDocument != null && !supportingDocument.isEmpty()) {
                try {
                    String supportingDocumentUrl = fileStorageService.storeSupportingDocument(supportingDocument);
                    wizardState.setSupportingDocumentOriginalName(this.cleanFilename(supportingDocument.getOriginalFilename()));
                    wizardState.setSupportingDocumentUrl(supportingDocumentUrl);
                } catch (IOException e) {
                    logger.warn("Failed to upload supporting document: {}", e.getMessage());
                }
            } else {
                wizardState.setSupportingDocumentOriginalName(null);
                wizardState.setSupportingDocumentUrl(null);
            }

            session.setAttribute(WIZARD_SESSION_KEY, wizardState);
            return "redirect:/vendor-tickets/new/step4";

        } catch (IOException e) {
            logger.error("Failed to upload invoice file: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to upload invoice file: " + e.getMessage());
            return "redirect:/vendor-tickets/new/step3";
        } catch (IllegalArgumentException e) {
            logger.error("File validation failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "File validation failed: " + e.getMessage());
            return "redirect:/vendor-tickets/new/step3";
        }
    }

    @GetMapping(value = {"/vendor-tickets/new/step4"})
    public String newTicketStep4(Authentication authentication, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = this.currentUser(authentication);
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/login";
        }
        VendorTicketWizardState wizardState = this.wizardState(session);
        if (wizardState == null || wizardState.getInvoiceFileUrl() == null) {
            redirectAttributes.addFlashAttribute("error", "Please complete the previous steps first.");
            return "redirect:/vendor-tickets/new";
        }
        model.addAttribute("vendor", currentUser.get());
        model.addAttribute("wizardState", wizardState);
        return "vendor-ticket-new-step4";
    }

    @PostMapping(value = {"/vendor-tickets/save"})
    public String saveVendorTicket(Authentication authentication, HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = this.currentUser(authentication);
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/login";
        }
        VendorTicketWizardState wizardState = this.wizardState(session);
        if (wizardState == null || wizardState.getInvoiceFileUrl() == null) {
            redirectAttributes.addFlashAttribute("error", "Please complete the previous steps first.");
            return "redirect:/vendor-tickets/new";
        }
        try {
            VendorTicket saved = this.vendorTicketService.saveWizardTicket(wizardState, authentication.getName());
            session.removeAttribute(WIZARD_SESSION_KEY);
            session.setAttribute(SUCCESS_TICKET_NO_KEY, saved.getTicketNo());
            return "redirect:/vendor-tickets/new/step5";
        }
        catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/vendor-tickets/new/step4";
        }
    }

    @GetMapping(value = {"/vendor-tickets/new/step5"})
    public String newTicketStep5(Authentication authentication, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = this.currentUser(authentication);
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/login";
        }

        Object ticketNo = session.getAttribute(SUCCESS_TICKET_NO_KEY);
        model.addAttribute("vendor", currentUser.get());
        model.addAttribute("ticketNo", ticketNo);
        session.removeAttribute(SUCCESS_TICKET_NO_KEY);
        return "vendor-ticket-new-step5";
    }

    @PostMapping(value = {"/vendor-tickets/new"})
    public String legacyCreateVendorTicket(@ModelAttribute VendorTicket vendorTicket, @RequestParam Long clientId, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            this.vendorTicketService.createVendorTicket(vendorTicket, clientId, authentication.getName());
            redirectAttributes.addFlashAttribute("message", "Vendor ticket created successfully.");
            return "redirect:/vendor-tickets";
        }
        catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error creating vendor ticket: " + e.getMessage());
            return "redirect:/vendor-tickets/new";
        }
    }

    private boolean isAdmin(AppUser user) {
        return user.getRoles().stream().anyMatch(role -> role.getName() != null && role.getName().equalsIgnoreCase("ROLE_ADMIN"));
    }

    private Optional<AppUser> currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.vendorTicketService.getVendorByUsername(authentication.getName()));
    }

    private VendorTicketWizardState wizardState(HttpSession session) {
        Object state = session.getAttribute(WIZARD_SESSION_KEY);
        if (state instanceof VendorTicketWizardState wizardState) {
            return wizardState;
        }
        return null;
    }

    private String cleanFilename(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

