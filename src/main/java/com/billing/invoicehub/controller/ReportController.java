package com.billing.invoicehub.controller;

import com.billing.invoicehub.dto.WeeklyTicketReportDto;
import com.billing.invoicehub.entity.TicketStatus;
import com.billing.invoicehub.entity.VendorTicket;
import com.billing.invoicehub.service.UserService;
import com.billing.invoicehub.service.ClientService;
import com.billing.invoicehub.service.ReportService;
import com.billing.invoicehub.service.VendorTicketService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ReportController {

    private final ReportService reportService;
    private final VendorTicketService vendorTicketService;
    private final UserService userService;
    private final ClientService clientService;

    public ReportController(ReportService reportService, VendorTicketService vendorTicketService,
                            UserService userService, ClientService clientService) {
        this.reportService = reportService;
        this.vendorTicketService = vendorTicketService;
        this.userService = userService;
        this.clientService = clientService;
    }

    @GetMapping("/admin/reports")
    public String viewReports(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) String poNumber,
            @RequestParam(required = false) String search,
            Model model) {

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : null;
        TicketStatus ticketStatus = parseStatus(status);
        String cleanPoNumber = cleanParam(poNumber);
        String cleanSearch = cleanParam(search);

        List<VendorTicket> tickets = reportService.getFilteredTickets(start, end, ticketStatus, vendorId, clientId, cleanPoNumber, cleanSearch);
        WeeklyTicketReportDto summary = reportService.calculateStatistics(tickets);

        model.addAttribute("tickets", tickets);
        model.addAttribute("weeklyReport", summary);
        model.addAttribute("vendors", userService.findVendors());
        model.addAttribute("clients", clientService.findAll());

        // Retain filters in view model
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("status", status != null ? status : "ALL");
        model.addAttribute("vendorId", vendorId);
        model.addAttribute("clientId", clientId);
        model.addAttribute("poNumber", poNumber);
        model.addAttribute("search", search);

        return "admin-reports";
    }

    @GetMapping("/admin/reports/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) String poNumber,
            @RequestParam(required = false) String search) {

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : null;
        TicketStatus ticketStatus = parseStatus(status);
        String cleanPoNumber = cleanParam(poNumber);
        String cleanSearch = cleanParam(search);

        List<VendorTicket> tickets = reportService.getFilteredTickets(start, end, ticketStatus, vendorId, clientId, cleanPoNumber, cleanSearch);
        WeeklyTicketReportDto summary = reportService.calculateStatistics(tickets);

        Map<String, String> filters = new HashMap<>();
        filters.put("Start Date", startDate != null ? startDate.toString() : "ALL");
        filters.put("End Date", endDate != null ? endDate.toString() : "ALL");
        filters.put("Status", status != null && !status.isEmpty() ? status : "ALL");
        filters.put("Vendor ID", vendorId != null ? String.valueOf(vendorId) : "ALL");
        filters.put("Client ID", clientId != null ? String.valueOf(clientId) : "ALL");
        filters.put("PO Number", cleanPoNumber != null ? cleanPoNumber : "ALL");
        filters.put("Search Query", cleanSearch != null ? cleanSearch : "ALL");

        byte[] pdf = reportService.generatePdf(tickets, filters, summary);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/admin/reports/csv")
    public ResponseEntity<byte[]> downloadCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) String poNumber,
            @RequestParam(required = false) String search) {

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : null;
        TicketStatus ticketStatus = parseStatus(status);
        String cleanPoNumber = cleanParam(poNumber);
        String cleanSearch = cleanParam(search);

        List<VendorTicket> tickets = reportService.getFilteredTickets(start, end, ticketStatus, vendorId, clientId, cleanPoNumber, cleanSearch);
        byte[] csv = reportService.generateCsv(tickets);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.csv\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(csv);
    }

    @GetMapping("/admin/reports/excel")
    public ResponseEntity<byte[]> downloadExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) String poNumber,
            @RequestParam(required = false) String search) throws IOException {

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : null;
        TicketStatus ticketStatus = parseStatus(status);
        String cleanPoNumber = cleanParam(poNumber);
        String cleanSearch = cleanParam(search);

        List<VendorTicket> tickets = reportService.getFilteredTickets(start, end, ticketStatus, vendorId, clientId, cleanPoNumber, cleanSearch);
        byte[] excel = reportService.generateExcel(tickets);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    private String cleanParam(String val) {
        if (val == null || val.trim().isEmpty()) {
            return null;
        }
        return val.trim();
    }

    private TicketStatus parseStatus(String raw) {
        if (raw == null || raw.trim().isEmpty() || "ALL".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return TicketStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
