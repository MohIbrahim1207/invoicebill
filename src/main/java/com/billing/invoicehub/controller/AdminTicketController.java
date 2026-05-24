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
import com.billing.invoicehub.entity.VendorTicketHistory;
import com.billing.invoicehub.service.VendorTicketService;
import java.time.LocalDateTime;
import java.util.List;
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
        Optional ticket = this.vendorTicketService.getTicketById(id);
        if (ticket.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Ticket not found.");
            return "redirect:/admin/tickets";
        }
        List history = this.vendorTicketService.getTicketHistory(id);
        model.addAttribute("ticket", ticket.get());
        model.addAttribute("history", (Object)history);
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
        vendorTicket.setStatusRequest(newStatus);
        this.vendorTicketService.updateTicket(vendorTicket);
        VendorTicketHistory history = new VendorTicketHistory(vendorTicket, newStatus, LocalDateTime.now(), comment != null ? comment.trim() : "");
        this.vendorTicketService.addTicketHistory(history);
        redirectAttributes.addFlashAttribute("message", (Object)"Ticket status updated successfully.");
        return "redirect:/admin/tickets/" + id + "/manage";
    }
}

