/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.controller.VendorTicketController
 *  com.billing.invoicehub.dto.VendorTicketWizardState
 *  com.billing.invoicehub.entity.AppUser
 *  com.billing.invoicehub.entity.TicketStatus
 *  com.billing.invoicehub.entity.VendorTicket
 *  com.billing.invoicehub.service.FileStorageService
 *  com.billing.invoicehub.service.VendorTicketService
 *  jakarta.servlet.http.HttpSession
 *  org.springframework.security.core.Authentication
 *  org.springframework.stereotype.Controller
 *  org.springframework.ui.Model
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.ModelAttribute
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.multipart.MultipartFile
 *  org.springframework.web.servlet.mvc.support.RedirectAttributes
 */
package com.billing.invoicehub.controller;

import com.billing.invoicehub.dto.VendorTicketWizardState;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.TicketStatus;
import com.billing.invoicehub.entity.VendorTicket;
import com.billing.invoicehub.service.FileStorageService;
import com.billing.invoicehub.service.VendorTicketService;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
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

@Controller
public class VendorTicketController {
    private static final String WIZARD_SESSION_KEY = "vendorTicketWizardState";
    private static final String SUCCESS_TICKET_NO_KEY = "vendorTicketSuccessTicketNo";
    private final VendorTicketService vendorTicketService;
    private final FileStorageService fileStorageService;

    public VendorTicketController(VendorTicketService vendorTicketService, FileStorageService fileStorageService) {
        this.vendorTicketService = vendorTicketService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping(value={"/vendor-tickets"})
    public String vendorTickets(@RequestParam(value="ticketNo", required=false) String ticketNo, @RequestParam(value="invoiceNo", required=false) String invoiceNo, @RequestParam(value="year", required=false) Integer year, @RequestParam(value="status", required=false, defaultValue="ALL") String status, Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        Optional currentUser = this.currentUser(authentication);
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please log in to continue.");
            return "redirect:/login";
        }
        boolean admin = this.isAdmin((AppUser)currentUser.get());
        List tickets = admin ? this.vendorTicketService.searchTickets(ticketNo, invoiceNo, year, status) : this.vendorTicketService.searchTicketsForOwner(((AppUser)currentUser.get()).getId(), ticketNo, invoiceNo, year, status);
        model.addAttribute("tickets", (Object)tickets);
        model.addAttribute("availableYears", (Object)(admin ? this.vendorTicketService.availableYears() : this.vendorTicketService.availableYearsForOwner(((AppUser)currentUser.get()).getId())));
        model.addAttribute("statusOptions", (Object)TicketStatus.values());
        model.addAttribute("ticketNo", (Object)ticketNo);
        model.addAttribute("invoiceNo", (Object)invoiceNo);
        model.addAttribute("year", (Object)year);
        model.addAttribute("status", (Object)(status == null ? "ALL" : status));
        return "vendor-tickets";
    }

    @PostMapping(value={"/vendor-tickets/{id}/cancel"})
    public String cancelTicket(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        Optional currentUser = this.currentUser(authentication);
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please log in to continue.");
            return "redirect:/login";
        }
        boolean cancelled = this.vendorTicketService.cancelTicket(id, authentication.getName());
        if (cancelled) {
            redirectAttributes.addFlashAttribute("message", (Object)"Ticket cancelled successfully.");
        } else {
            redirectAttributes.addFlashAttribute("error", (Object)"Ticket not found or access denied.");
        }
        return "redirect:/vendor-tickets";
    }

    @GetMapping(value={"/vendor-tickets/{id}/history"})
    public String ticketHistory(@PathVariable Long id, Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        Optional currentUser = this.currentUser(authentication);
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please log in to continue.");
            return "redirect:/login";
        }
        Optional ticket = this.vendorTicketService.getAccessibleTicket(id, authentication.getName());
        if (ticket.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Ticket not found or access denied.");
            return "redirect:/vendor-tickets";
        }
        List history = this.vendorTicketService.getTicketHistory(id);
        model.addAttribute("ticket", ticket.get());
        model.addAttribute("history", (Object)history);
        return "ticket-history";
    }

    @GetMapping(value={"/vendor-tickets/new"})
    public String newTicketStep2(Authentication authentication, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Optional currentUser = this.currentUser(authentication);
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please log in to continue.");
            return "redirect:/login";
        }
        VendorTicketWizardState wizardState = this.wizardState(session);
        if (wizardState == null) {
            wizardState = new VendorTicketWizardState();
        }
        wizardState.setClientId(null);
        wizardState.setClientName(((AppUser)currentUser.get()).getUsername());
        model.addAttribute("vendor", currentUser.get());
        model.addAttribute("wizardState", (Object)wizardState);
        return "vendor-ticket-new-step2";
    }

    @PostMapping(value={"/vendor-tickets/new/step3"})
    public String saveStep2(@ModelAttribute VendorTicketWizardState wizardState, Authentication authentication, HttpSession session, RedirectAttributes redirectAttributes) {
        Optional currentUser = this.currentUser(authentication);
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please log in to continue.");
            return "redirect:/login";
        }
        wizardState.setClientName(((AppUser)currentUser.get()).getUsername());
        session.setAttribute(WIZARD_SESSION_KEY, (Object)wizardState);
        return "redirect:/vendor-tickets/new/step3";
    }

    @GetMapping(value={"/vendor-tickets/new/step3"})
    public String newTicketStep3(Authentication authentication, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Optional currentUser = this.currentUser(authentication);
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please log in to continue.");
            return "redirect:/login";
        }
        VendorTicketWizardState wizardState = this.wizardState(session);
        if (wizardState == null) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please complete step 2 first.");
            return "redirect:/vendor-tickets/new";
        }
        model.addAttribute("vendor", currentUser.get());
        model.addAttribute("wizardState", (Object)wizardState);
        return "vendor-ticket-new-step3";
    }

    @PostMapping(value={"/vendor-tickets/new/step4"})
    public String saveStep3(@RequestParam(value="invoiceFile") MultipartFile invoiceFile, @RequestParam(value="taxDocument", required=false) MultipartFile taxDocument, @RequestParam(value="poCopy", required=false) MultipartFile poCopy, @RequestParam(value="deliveryNote", required=false) MultipartFile deliveryNote, @RequestParam(value="otherDocument", required=false) MultipartFile otherDocument, @RequestParam(value="supportingDocument", required=false) MultipartFile supportingDocument, Authentication authentication, HttpSession session, RedirectAttributes redirectAttributes) {
        Optional currentUser = this.currentUser(authentication);
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please log in to continue.");
            return "redirect:/login";
        }
        VendorTicketWizardState wizardState = this.wizardState(session);
        if (wizardState == null) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please complete step 2 first.");
            return "redirect:/vendor-tickets/new";
        }
        if (invoiceFile == null || invoiceFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please upload the invoice file.");
            return "redirect:/vendor-tickets/new/step3";
        }
        try {
            wizardState.setInvoiceFileOriginalName(this.cleanFilename(invoiceFile.getOriginalFilename()));
            wizardState.setInvoiceFileName(this.fileStorageService.storeInvoiceFile(invoiceFile));
            if (wizardState.getTicketNo() == null) {
                wizardState.setTicketNo(this.vendorTicketService.generateTicketNo());
            }
            if (taxDocument != null && !taxDocument.isEmpty()) {
                wizardState.setTaxDocumentOriginalName(this.cleanFilename(taxDocument.getOriginalFilename()));
                wizardState.setTaxDocumentName(this.fileStorageService.storeInvoiceFile(taxDocument));
            } else {
                wizardState.setTaxDocumentOriginalName(null);
                wizardState.setTaxDocumentName(null);
            }
            if (poCopy != null && !poCopy.isEmpty()) {
                wizardState.setPoCopyOriginalName(this.cleanFilename(poCopy.getOriginalFilename()));
                wizardState.setPoCopyName(this.fileStorageService.storeInvoiceFile(poCopy));
            } else {
                wizardState.setPoCopyOriginalName(null);
                wizardState.setPoCopyName(null);
            }
            if (deliveryNote != null && !deliveryNote.isEmpty()) {
                wizardState.setDeliveryNoteOriginalName(this.cleanFilename(deliveryNote.getOriginalFilename()));
                wizardState.setDeliveryNoteName(this.fileStorageService.storeInvoiceFile(deliveryNote));
            } else {
                wizardState.setDeliveryNoteOriginalName(null);
                wizardState.setDeliveryNoteName(null);
            }
            if (otherDocument != null && !otherDocument.isEmpty()) {
                wizardState.setOtherDocumentOriginalName(this.cleanFilename(otherDocument.getOriginalFilename()));
                wizardState.setOtherDocumentName(this.fileStorageService.storeInvoiceFile(otherDocument));
            } else {
                wizardState.setOtherDocumentOriginalName(null);
                wizardState.setOtherDocumentName(null);
            }
            if (supportingDocument != null && !supportingDocument.isEmpty()) {
                wizardState.setSupportingDocumentOriginalName(this.cleanFilename(supportingDocument.getOriginalFilename()));
                wizardState.setSupportingDocumentName(this.fileStorageService.storeSupportingDocument(supportingDocument));
            } else {
                wizardState.setSupportingDocumentOriginalName(null);
                wizardState.setSupportingDocumentName(null);
            }
            session.setAttribute(WIZARD_SESSION_KEY, (Object)wizardState);
            return "redirect:/vendor-tickets/new/step4";
        }
        catch (IOException ex) {
            redirectAttributes.addFlashAttribute("error", (Object)"Could not save the uploaded file.");
            return "redirect:/vendor-tickets/new/step3";
        }
    }

    @GetMapping(value={"/vendor-tickets/new/step4"})
    public String newTicketStep4(Authentication authentication, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Optional currentUser = this.currentUser(authentication);
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please log in to continue.");
            return "redirect:/login";
        }
        VendorTicketWizardState wizardState = this.wizardState(session);
        if (wizardState == null || wizardState.getInvoiceFileName() == null) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please complete the previous steps first.");
            return "redirect:/vendor-tickets/new";
        }
        model.addAttribute("vendor", currentUser.get());
        model.addAttribute("wizardState", (Object)wizardState);
        return "vendor-ticket-new-step4";
    }

    @PostMapping(value={"/vendor-tickets/save"})
    public String saveVendorTicket(Authentication authentication, HttpSession session, RedirectAttributes redirectAttributes) {
        Optional currentUser = this.currentUser(authentication);
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please log in to continue.");
            return "redirect:/login";
        }
        VendorTicketWizardState wizardState = this.wizardState(session);
        if (wizardState == null || wizardState.getInvoiceFileName() == null) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please complete the previous steps first.");
            return "redirect:/vendor-tickets/new";
        }
        try {
            VendorTicket saved = this.vendorTicketService.saveWizardTicket(wizardState, authentication.getName());
            session.removeAttribute(WIZARD_SESSION_KEY);
            session.setAttribute(SUCCESS_TICKET_NO_KEY, (Object)saved.getTicketNo());
            return "redirect:/vendor-tickets/new/step5";
        }
        catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", (Object)ex.getMessage());
            return "redirect:/vendor-tickets/new/step4";
        }
    }

    @GetMapping(value={"/vendor-tickets/new/step5"})
    public String newTicketStep5(Authentication authentication, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Optional currentUser = this.currentUser(authentication);
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please log in to continue.");
            return "redirect:/login";
        }
        Object ticketNo = session.getAttribute(SUCCESS_TICKET_NO_KEY);
        model.addAttribute("vendor", currentUser.get());
        model.addAttribute("ticketNo", ticketNo);
        session.removeAttribute(SUCCESS_TICKET_NO_KEY);
        return "vendor-ticket-new-step5";
    }

    @PostMapping(value={"/vendor-tickets/new"})
    public String legacyCreateVendorTicket(@ModelAttribute VendorTicket vendorTicket, @RequestParam Long clientId, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            this.vendorTicketService.createVendorTicket(vendorTicket, clientId, authentication.getName());
            redirectAttributes.addFlashAttribute("message", (Object)"Vendor ticket created successfully.");
            return "redirect:/vendor-tickets";
        }
        catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", (Object)("Error creating vendor ticket: " + e.getMessage()));
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
        if (state instanceof VendorTicketWizardState) {
            VendorTicketWizardState wizardState = (VendorTicketWizardState)state;
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

