/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.controller.AdminTicketController
 *  com.billing.invoicehub.entity.TicketStatus
 *  com.billing.invoicehub.entity.VendorTicket
 *  com.billing.invoicehub.entity.VendorTicketHistory
 *  com.billing.invoicehub.service.VendorTicketService
 *  org.springframework.stereotype.Controller
 *  org.springframework.ui.Model
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.servlet.mvc.support.RedirectAttributes
 */
package com.billing.invoicehub.controller;

import com.billing.invoicehub.entity.TicketStatus;
import com.billing.invoicehub.entity.VendorTicket;
import com.billing.invoicehub.service.VendorTicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping(value={"/admin/tickets"})
public class AdminTicketController {
    private static final Logger log = LoggerFactory.getLogger(AdminTicketController.class);
    private final VendorTicketService vendorTicketService;

    public AdminTicketController(VendorTicketService vendorTicketService) {
        this.vendorTicketService = vendorTicketService;
    }

    @GetMapping
    public String adminTickets(@RequestParam(value="ticketNo", required=false) String ticketNo, @RequestParam(value="invoiceNo", required=false) String invoiceNo, @RequestParam(value="year", required=false) Integer year, @RequestParam(value="status", required=false, defaultValue="ALL") String status, Model model) {
        List tickets = this.vendorTicketService.searchTickets(ticketNo, invoiceNo, year, status);
        model.addAttribute("tickets", (Object)tickets);
        model.addAttribute("availableYears", (Object)this.vendorTicketService.availableYears());
        model.addAttribute("statusOptions", (Object)TicketStatus.values());
        model.addAttribute("ticketNo", (Object)ticketNo);
        model.addAttribute("invoiceNo", (Object)invoiceNo);
        model.addAttribute("year", (Object)year);
        model.addAttribute("status", (Object)(status == null ? "ALL" : status));
        return "admin-tickets";
    }

    @GetMapping(value={"/{id}/manage"})
    public String manageTicket(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Loading ticket {} for management", id);
        Optional ticket = this.vendorTicketService.getTicketById(id);
        if (ticket.isEmpty()) {
            log.warn("Ticket {} not found", id);
            redirectAttributes.addFlashAttribute("error", (Object)"Ticket not found.");
            return "redirect:/admin/tickets";
        }
        
        VendorTicket vendorTicket = (VendorTicket) ticket.get();
        List history = this.vendorTicketService.getTicketHistory(id);
        
        // Build document information map for template
        Map<String, Map<String, String>> documents = buildDocumentMap(vendorTicket);
        
        log.debug("Loaded {} documents for ticket {}", documents.size(), id);
        model.addAttribute("ticket", vendorTicket);
        model.addAttribute("history", (Object)history);
        model.addAttribute("documents", documents);
        model.addAttribute("statusOptions", (Object)TicketStatus.values());
        return "admin-ticket-manage";
    }

    @PostMapping(value={"/{id}/update-status"})
    public String updateTicketStatus(@PathVariable Long id, @RequestParam TicketStatus newStatus, @RequestParam(required=false) String comment, RedirectAttributes redirectAttributes) {
        Optional ticket = this.vendorTicketService.getTicketById(id);
        if (ticket.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Ticket not found.");
            return "redirect:/admin/tickets";
        }
        if (!(newStatus != TicketStatus.REVISE && newStatus != TicketStatus.CANCEL || comment != null && !comment.trim().isEmpty())) {
            redirectAttributes.addFlashAttribute("error", (Object)("Comment is required for " + newStatus.name() + " status."));
            return "redirect:/admin/tickets/" + id + "/manage";
        }
        VendorTicket vendorTicket = (VendorTicket)ticket.get();
        this.vendorTicketService.updateTicketStatusAndNotify(vendorTicket, newStatus, comment);
        redirectAttributes.addFlashAttribute("message", (Object)"Ticket status updated successfully.");
        return "redirect:/admin/tickets/" + id + "/manage";
    }
    
    /**
     * Build a map of documents with their metadata for template rendering.
     * Each document includes: name, url, fileType, exists flag
     */
    private Map<String, Map<String, String>> buildDocumentMap(VendorTicket ticket) {
        Map<String, Map<String, String>> documents = new HashMap<>();
        
        // Invoice File
        if (ticket.getInvoiceFileUrl() != null && !ticket.getInvoiceFileUrl().isBlank()) {
            documents.put("invoice", createDocumentEntry(
                "Invoice Document",
                ticket.getInvoiceFileUrl(),
                ticket.getInvoiceFileName()
            ));
        }
        
        // Tax Document
        if (ticket.getTaxDocumentUrl() != null && !ticket.getTaxDocumentUrl().isBlank()) {
            documents.put("tax", createDocumentEntry(
                "Tax Document",
                ticket.getTaxDocumentUrl(),
                null
            ));
        }
        
        // PO Copy
        if (ticket.getPoCopyUrl() != null && !ticket.getPoCopyUrl().isBlank()) {
            documents.put("po", createDocumentEntry(
                "Purchase Order Copy",
                ticket.getPoCopyUrl(),
                null
            ));
        }
        
        // Delivery Note
        if (ticket.getDeliveryNoteUrl() != null && !ticket.getDeliveryNoteUrl().isBlank()) {
            documents.put("delivery", createDocumentEntry(
                "Delivery Note",
                ticket.getDeliveryNoteUrl(),
                null
            ));
        }
        
        // Other Document
        if (ticket.getOtherDocumentUrl() != null && !ticket.getOtherDocumentUrl().isBlank()) {
            documents.put("other", createDocumentEntry(
                "Other Document",
                ticket.getOtherDocumentUrl(),
                null
            ));
        }
        
        return documents;
    }
    
    /**
     * Create a document entry with metadata
     */
    private Map<String, String> createDocumentEntry(String name, String url, String originalFilename) {
        Map<String, String> entry = new HashMap<>();
        entry.put("name", name);
        entry.put("url", url);
        entry.put("fileType", extractFileType(url, originalFilename));
        entry.put("icon", getIconForFileType(entry.get("fileType")));
        return entry;
    }
    
    /**
     * Extract file type from URL or filename
     */
    private String extractFileType(String url, String filename) {
        if (filename != null && filename.contains(".")) {
            String ext = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
            return ext;
        }
        
        if (url != null && url.contains(".")) {
            // Extract from URL (e.g., .jpg, .pdf)
            String[] parts = url.split("\\.");
            if (parts.length > 0) {
                String ext = parts[parts.length - 1].split("[?#]")[0].toLowerCase();
                if (ext.length() <= 5) { // Reasonable extension length
                    return ext;
                }
            }
        }
        return "file";
    }
    
    /**
     * Map file type to Bootstrap icon class
     */
    private String getIconForFileType(String fileType) {
        if (fileType == null) return "bi-file";
        
        return switch (fileType.toLowerCase()) {
            case "pdf" -> "bi-file-pdf";
            case "jpg", "jpeg", "png", "webp", "gif" -> "bi-image";
            case "doc", "docx" -> "bi-file-word";
            case "xls", "xlsx" -> "bi-file-earmark-excel";
            case "zip", "rar" -> "bi-file-earmark-zip";
            default -> "bi-file";
        };
    }
}
