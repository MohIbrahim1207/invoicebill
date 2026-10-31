package com.billing.invoicehub.controller;

import com.billing.invoicehub.entity.TicketStatus;
import com.billing.invoicehub.entity.VendorTicket;
import com.billing.invoicehub.service.AiQuoteConversionService;
import com.billing.invoicehub.service.DashboardService;
import com.billing.invoicehub.service.VendorTicketService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminController {
    private final VendorTicketService vendorTicketService;
    private final AiQuoteConversionService aiQuoteConversionService;
    private final DashboardService dashboardService;

    public AdminController(VendorTicketService vendorTicketService,
                           AiQuoteConversionService aiQuoteConversionService,
                           DashboardService dashboardService) {
        this.vendorTicketService = vendorTicketService;
        this.aiQuoteConversionService = aiQuoteConversionService;
        this.dashboardService = dashboardService;
    }

    @GetMapping(value={"/ticket-history"})
    public String ticketHistory() {
        return "redirect:/admin/vendor-tickets";
    }

    @GetMapping(value={"/admin/vendor-tickets"})
    public String adminVendorTickets(@RequestParam(value="ticketNo", required=false) String ticketNo,
                                     @RequestParam(value="invoiceNo", required=false) String invoiceNo,
                                     @RequestParam(value="year", required=false) Integer year,
                                     @RequestParam(value="status", required=false, defaultValue="ALL") String status,
                                     Model model) {
        List<VendorTicket> tickets = this.vendorTicketService.searchTickets(ticketNo, invoiceNo, year, status);
        model.addAttribute("tickets", tickets);
        model.addAttribute("availableYears", this.vendorTicketService.availableYears());
        model.addAttribute("statusOptions", TicketStatus.values());
        model.addAttribute("ticketNo", ticketNo);
        model.addAttribute("invoiceNo", invoiceNo);
        model.addAttribute("year", year);
        model.addAttribute("status", (status == null ? "ALL" : status));
        return "admin-tickets";
    }

    @GetMapping(value={"/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("aiStats", aiQuoteConversionService.getStatistics());
        model.addAttribute("dashboardStats", dashboardService.getDashboardStats());
        return "dashboard";
    }
}
