/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.controller.AdminController
 *  com.billing.invoicehub.entity.TicketStatus
 *  com.billing.invoicehub.service.VendorTicketService
 *  org.springframework.stereotype.Controller
 *  org.springframework.ui.Model
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.RequestParam
 */
package com.billing.invoicehub.controller;

import com.billing.invoicehub.entity.TicketStatus;
import com.billing.invoicehub.entity.VendorTicket;
import com.billing.invoicehub.service.VendorTicketService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminController {
    private final VendorTicketService vendorTicketService;

    public AdminController(VendorTicketService vendorTicketService) {
        this.vendorTicketService = vendorTicketService;
    }

    @GetMapping(value={"/ticket-history"})
    public String ticketHistory() {
        return "redirect:/admin/vendor-tickets";
    }

    @GetMapping(value={"/admin/vendor-tickets"})
    public String adminVendorTickets(@RequestParam(value="ticketNo", required=false) String ticketNo, @RequestParam(value="invoiceNo", required=false) String invoiceNo, @RequestParam(value="year", required=false) Integer year, @RequestParam(value="status", required=false, defaultValue="ALL") String status, Model model) {
        List<VendorTicket> tickets = this.vendorTicketService.searchTickets(ticketNo, invoiceNo, year, status);
        model.addAttribute("tickets", (Object)tickets);
        model.addAttribute("availableYears", (Object)this.vendorTicketService.availableYears());
        model.addAttribute("statusOptions", (Object)TicketStatus.values());
        model.addAttribute("ticketNo", (Object)ticketNo);
        model.addAttribute("invoiceNo", (Object)invoiceNo);
        model.addAttribute("year", (Object)year);
        model.addAttribute("status", (Object)(status == null ? "ALL" : status));
        return "admin-tickets";
    }

    @GetMapping(value={"/dashboard"})
    public String dashboard() {
        return "dashboard";
    }
}

