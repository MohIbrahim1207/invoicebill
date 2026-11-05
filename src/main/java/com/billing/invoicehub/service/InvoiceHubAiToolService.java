package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.DashboardStatsDto;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.Invoice;
import com.billing.invoicehub.entity.TicketStatus;
import com.billing.invoicehub.entity.VendorTicket;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.InvoiceRepository;
import com.billing.invoicehub.repository.VendorTicketRepository;
import com.billing.invoicehub.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Read-only tools for InvoiceHub Assistant with strict role-based permission enforcement in Java.
 *
 * Rules:
 * 1. Staff (ADMIN, SUPER_ADMIN, ACCOUNTS) can view system-wide records.
 * 2. Vendors see ONLY their own records (cross-vendor queries return 'not found').
 * 3. Results contain ONLY non-sensitive fields (NO bank accounts, NO NPWP, NO passwords, NO raw doc URLs).
 * 4. Read-only only (no write/modification actions allowed).
 */
@Service
@Transactional(readOnly = true)
public class InvoiceHubAiToolService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceHubAiToolService.class);

    private final VendorTicketRepository vendorTicketRepository;
    private final InvoiceRepository invoiceRepository;
    private final AppUserRepository userRepository;
    private final SidebarBadgeService sidebarBadgeService;
    private final DashboardService dashboardService;

    public InvoiceHubAiToolService(VendorTicketRepository vendorTicketRepository,
                                  InvoiceRepository invoiceRepository,
                                  AppUserRepository userRepository,
                                  SidebarBadgeService sidebarBadgeService,
                                  DashboardService dashboardService) {
        this.vendorTicketRepository = vendorTicketRepository;
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
        this.sidebarBadgeService = sidebarBadgeService;
        this.dashboardService = dashboardService;
    }

    public boolean isStaff(AppUser user) {
        if (user == null) {
            return false;
        }
        if ("INTERNAL".equalsIgnoreCase(user.getUserDomain())) {
            return true;
        }
        if (user.getRoles() != null) {
            try {
                boolean hasStaffRole = user.getRoles().stream().anyMatch(r ->
                        "ROLE_ADMIN".equals(r.getName())
                                || "ROLE_SUPER_ADMIN".equals(r.getName())
                                || "ROLE_ACCOUNTS".equals(r.getName()));
                if (hasStaffRole) return true;
            } catch (Exception ignored) {}
        }
        if (user.getId() != null) {
            return userRepository.findById(user.getId()).map(u -> {
                if ("INTERNAL".equalsIgnoreCase(u.getUserDomain())) return true;
                return u.getRoles() != null && u.getRoles().stream().anyMatch(r ->
                        "ROLE_ADMIN".equals(r.getName())
                                || "ROLE_SUPER_ADMIN".equals(r.getName())
                                || "ROLE_ACCOUNTS".equals(r.getName()));
            }).orElse(false);
        }
        return false;
    }

    /**
     * Tool 1: searchTickets(status, vendor, from, to)
     */
    public Map<String, Object> searchTickets(AppUser currentUser, String status, String vendor, String from, String to) {
        if (currentUser == null) {
            return Map.of("found", false, "message", "Authentication required to search tickets.");
        }

        boolean staff = isStaff(currentUser);
        LocalDateTime fromDate = null;
        LocalDateTime toDate = null;

        if (from != null && !from.isBlank()) {
            try {
                fromDate = LocalDate.parse(from.trim()).atStartOfDay();
            } catch (Exception ignored) {}
        }
        if (to != null && !to.isBlank()) {
            try {
                toDate = LocalDate.parse(to.trim()).atTime(LocalTime.MAX);
            } catch (Exception ignored) {}
        }

        TicketStatus ticketStatus = parseTicketStatus(status);

        List<VendorTicket> matchedTickets;
        if (staff) {
            Long filterVendorId = null;
            if (vendor != null && !vendor.isBlank()) {
                Optional<AppUser> vOpt = userRepository.findFirstByCompanyNameIgnoreCase(vendor.trim())
                        .or(() -> userRepository.findFirstByVendorCode(vendor.trim()))
                        .or(() -> userRepository.findFirstByUsername(vendor.trim()));
                if (vOpt.isPresent()) {
                    filterVendorId = vOpt.get().getId();
                }
            }

            if (ticketStatus == null) {
                // Smart default: latest open/pending tickets first; if none, latest tickets of any status
                List<VendorTicket> openTickets = vendorTicketRepository.searchTicketsAdvanced(
                        fromDate, toDate, TicketStatus.OPEN, filterVendorId, null, null, null);
                if (!openTickets.isEmpty()) {
                    matchedTickets = openTickets;
                } else {
                    matchedTickets = vendorTicketRepository.searchTicketsAdvanced(
                            fromDate, toDate, null, filterVendorId, null, null, null);
                }
            } else {
                matchedTickets = vendorTicketRepository.searchTicketsAdvanced(
                        fromDate, toDate, ticketStatus, filterVendorId, null, null, null);
            }
        } else {
            // Strictly isolate to currentUser's own tickets (ignore any requested vendor)
            Long vendorId = currentUser.getId();
            if (ticketStatus == null) {
                List<VendorTicket> openTickets = vendorTicketRepository.searchTicketsAdvanced(
                        fromDate, toDate, TicketStatus.OPEN, vendorId, null, null, null);
                if (!openTickets.isEmpty()) {
                    matchedTickets = openTickets;
                } else {
                    matchedTickets = vendorTicketRepository.searchTicketsAdvanced(
                            fromDate, toDate, null, vendorId, null, null, null);
                }
            } else {
                matchedTickets = vendorTicketRepository.searchTicketsAdvanced(
                        fromDate, toDate, ticketStatus, vendorId, null, null, null);
            }
        }

        List<Map<String, Object>> sanitizedList = new ArrayList<>();
        int count = 0;
        for (VendorTicket t : matchedTickets) {
            if (count++ >= 10) break; // Smart default: latest 10 tickets for chat display

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("found", true);
            item.put("ticketNumber", t.getTicketNo());
            item.put("invoiceNumber", t.getInvoiceNo());
            item.put("vendorName", t.getVendor() != null ? t.getVendor().getCompanyName() : "N/A");
            item.put("status", t.getStatusRequest() != null ? t.getStatusRequest().name() : "UNKNOWN");
            item.put("amount", formatCurrency(t.getAmount() != null ? t.getAmount() : t.getTotal(), t.getCurrency()));
            item.put("date", t.getCreatedAt() != null ? t.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "N/A");
            item.put("url", staff ? ("/admin/tickets/detail?id=" + t.getId()) : "/vendor-tickets");
            item.put("actionLabel", "Open ticket");
            sanitizedList.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", !sanitizedList.isEmpty());
        result.put("count", sanitizedList.size());
        result.put("tickets", sanitizedList);
        if (sanitizedList.isEmpty()) {
            result.put("message", "No tickets found matching the specified criteria.");
        }
        return result;
    }

    /**
     * Tool 2: getTicket(ticketNumber)
     */
    public Map<String, Object> getTicket(AppUser currentUser, String ticketNumber) {
        if (currentUser == null) {
            return Map.of("found", false, "message", "Authentication required to view ticket details.");
        }

        boolean staff = isStaff(currentUser);
        String search = (ticketNumber != null) ? ticketNumber.trim() : "";
        Optional<VendorTicket> ticketOpt;

        if (search.isBlank()) {
            // Smart default: latest ticket for current user
            List<VendorTicket> latest = staff
                    ? vendorTicketRepository.searchTicketsAdvanced(null, null, null, null, null, null, null)
                    : vendorTicketRepository.searchTicketsAdvanced(null, null, null, currentUser.getId(), null, null, null);
            if (latest.isEmpty()) {
                return Map.of("found", false, "message", "No tickets found for current account.");
            }
            ticketOpt = Optional.of(latest.get(0));
            search = ticketOpt.get().getTicketNo();
        } else {
            ticketOpt = vendorTicketRepository.findFirstByTicketNoIgnoreCase(search);
            if (ticketOpt.isEmpty()) {
                List<VendorTicket> byInvoice = vendorTicketRepository.findByInvoiceNoIgnoreCase(search);
                if (!byInvoice.isEmpty()) {
                    ticketOpt = Optional.of(byInvoice.get(0));
                }
            }
            if (ticketOpt.isEmpty() && search.matches("\\d+")) {
                ticketOpt = vendorTicketRepository.findById(Long.parseLong(search));
            }
        }

        if (ticketOpt.isEmpty()) {
            return Map.of("found", false, "message", "Ticket '" + search + "' not found.");
        }

        VendorTicket ticket = ticketOpt.get();

        // Security Check: Non-staff can ONLY see their own tickets
        if (!staff) {
            boolean isOwner = (ticket.getOwner() != null && ticket.getOwner().getId().equals(currentUser.getId()))
                    || (ticket.getVendor() != null && ticket.getVendor().getId().equals(currentUser.getId()));
            if (!isOwner) {
                // Must return "not found" so vendor A cannot verify vendor B's ticket existence
                log.warn("AI Tool: Vendor '{}' (id={}) attempted access to ticket '{}' owned by another vendor. Returning 'not found'.",
                        currentUser.getUsername(), currentUser.getId(), ticket.getTicketNo());
                return Map.of("found", false, "message", "Ticket '" + search + "' not found.");
            }
        }

        // Return strictly sanitized fields (no bank account, no NPWP, no raw file URLs)
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("found", true);
        data.put("ticketNumber", ticket.getTicketNo());
        data.put("invoiceNumber", ticket.getInvoiceNo());
        data.put("vendorName", ticket.getVendor() != null ? ticket.getVendor().getCompanyName() : "N/A");
        data.put("status", ticket.getStatusRequest() != null ? ticket.getStatusRequest().name() : "UNKNOWN");
        data.put("amount", formatCurrency(ticket.getAmount() != null ? ticket.getAmount() : ticket.getTotal(), ticket.getCurrency()));
        data.put("date", ticket.getCreatedAt() != null ? ticket.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "N/A");
        data.put("notes", ticket.getVendorRemarks() != null ? sanitizeNote(ticket.getVendorRemarks()) : "No notes provided");
        data.put("url", staff ? ("/admin/tickets/detail?id=" + ticket.getId()) : "/vendor-tickets");
        data.put("actionLabel", "Open ticket");
        return data;
    }

    /**
     * Tool 3: getInvoiceStatus(invoiceNumber)
     */
    public Map<String, Object> getInvoiceStatus(AppUser currentUser, String invoiceNumber) {
        if (currentUser == null) {
            return Map.of("found", false, "message", "Authentication required to view invoice status.");
        }

        boolean staff = isStaff(currentUser);
        String search = (invoiceNumber != null) ? invoiceNumber.trim() : "";
        Optional<Invoice> invoiceOpt;

        if (search.isBlank()) {
            // Smart default: invoice from latest ticket
            List<VendorTicket> latestTickets = staff
                    ? vendorTicketRepository.searchTicketsAdvanced(null, null, null, null, null, null, null)
                    : vendorTicketRepository.searchTicketsAdvanced(null, null, null, currentUser.getId(), null, null, null);
            Optional<VendorTicket> withInvoice = latestTickets.stream().filter(t -> t.getInvoice() != null).findFirst();
            if (withInvoice.isPresent()) {
                invoiceOpt = Optional.of(withInvoice.get().getInvoice());
                search = invoiceOpt.get().getInvoiceNumber();
            } else {
                return Map.of("found", false, "message", "No invoices found for current account.");
            }
        } else {
            invoiceOpt = invoiceRepository.findFirstByInvoiceNumberIgnoreCaseOrderByIdDesc(search);
            if (invoiceOpt.isEmpty() && search.matches("\\d+")) {
                invoiceOpt = invoiceRepository.findById(Long.parseLong(search));
            }
        }

        if (invoiceOpt.isEmpty()) {
            return Map.of("found", false, "message", "Invoice '" + search + "' not found.");
        }

        Invoice invoice = invoiceOpt.get();

        // Security Check: Non-staff can ONLY see invoices they own or linked to their ticket
        if (!staff) {
            boolean isClientOwner = invoice.getClient() != null
                    && invoice.getClient().getOwner() != null
                    && invoice.getClient().getOwner().getId().equals(currentUser.getId());

            boolean isLinkedTicketOwner = vendorTicketRepository.findAllByInvoice_Id(invoice.getId()).stream()
                    .anyMatch(vt -> (vt.getOwner() != null && vt.getOwner().getId().equals(currentUser.getId()))
                            || (vt.getVendor() != null && vt.getVendor().getId().equals(currentUser.getId())));

            if (!isClientOwner && !isLinkedTicketOwner) {
                log.warn("AI Tool: Vendor '{}' (id={}) attempted access to invoice '{}' owned by another vendor. Returning 'not found'.",
                        currentUser.getUsername(), currentUser.getId(), invoice.getInvoiceNumber());
                return Map.of("found", false, "message", "Invoice '" + search + "' not found.");
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("found", true);
        data.put("invoiceNumber", invoice.getInvoiceNumber());
        data.put("status", invoice.getStatus() != null ? invoice.getStatus() : "Pending");
        data.put("amount", formatCurrency(invoice.getAmount(), "IDR"));
        String dateStr = invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().toString()
                : (invoice.getInvoiceDateRaw() != null ? invoice.getInvoiceDateRaw() : "N/A");
        data.put("invoiceDate", dateStr);
        data.put("url", staff ? ("/invoices/" + invoice.getId()) : ("/invoice/view/" + invoice.getId()));
        data.put("actionLabel", "View invoice");
        return data;
    }

    /**
     * Tool 4: getVendor(nameOrCode)
     */
    public Map<String, Object> getVendor(AppUser currentUser, String nameOrCode) {
        if (currentUser == null) {
            return Map.of("found", false, "message", "Authentication required to view vendor details.");
        }

        boolean staff = isStaff(currentUser);
        String search = (nameOrCode != null) ? nameOrCode.trim() : "";

        if (search.isBlank()) {
            if (!staff) {
                // Smart default: current vendor's profile
                return sanitizeVendorInfo(currentUser, false);
            }
            return Map.of("found", false, "message", "Please specify a vendor name or vendor code.");
        }

        if (!staff) {
            // Non-staff vendors may ONLY query their own company
            boolean matchesOwn = (currentUser.getCompanyName() != null && currentUser.getCompanyName().equalsIgnoreCase(search))
                    || (currentUser.getVendorCode() != null && currentUser.getVendorCode().equalsIgnoreCase(search))
                    || (currentUser.getUsername() != null && currentUser.getUsername().equalsIgnoreCase(search));

            if (!matchesOwn) {
                return Map.of("found", false, "message", "Vendor '" + search + "' not found.");
            }
            return sanitizeVendorInfo(currentUser, false);
        }

        // Staff can search by vendor code, company name, or username
        Optional<AppUser> vendorOpt = userRepository.findFirstByVendorCode(search)
                .or(() -> userRepository.findFirstByCompanyNameIgnoreCase(search))
                .or(() -> userRepository.findFirstByUsername(search));

        if (vendorOpt.isEmpty()) {
            return Map.of("found", false, "message", "Vendor '" + search + "' not found.");
        }

        return sanitizeVendorInfo(vendorOpt.get(), true);
    }

    /**
     * Tool 5: getDashboardSummary()
     */
    public Map<String, Object> getDashboardSummary(AppUser currentUser) {
        if (currentUser == null) {
            return Map.of("found", false, "message", "Authentication required to view dashboard metrics.");
        }

        boolean staff = isStaff(currentUser);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("found", true);

        if (staff) {
            DashboardStatsDto stats = dashboardService.getDashboardStats();
            summary.put("role", "STAFF");
            summary.put("pendingInvoices", stats.getPendingInvoices());
            summary.put("approvedInvoices", stats.getApprovedInvoices());
            summary.put("totalInvoices", stats.getTotalInvoices());
            summary.put("totalAmount", stats.getTotalAmountCompact());
            summary.put("unverifiedVendors", sidebarBadgeService.getUnverifiedVendorsCount());
            summary.put("openTickets", sidebarBadgeService.getOpenTicketsCount());
            summary.put("url", "/dashboard");
            summary.put("actionLabel", "Go to Dashboard");
        } else {
            long myInvoices = invoiceRepository.countByClient_Owner_Id(currentUser.getId());
            long pendingInvoices = invoiceRepository.countByStatusIgnoreCaseAndClient_Owner_Id("Pending", currentUser.getId());
            long openTickets = vendorTicketRepository.countByStatusRequestInAndOwner_Id(
                    List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS), currentUser.getId());

            summary.put("role", "VENDOR");
            summary.put("vendorCompany", currentUser.getCompanyName() != null ? currentUser.getCompanyName() : currentUser.getUsername());
            summary.put("totalInvoices", myInvoices);
            summary.put("pendingInvoices", pendingInvoices);
            summary.put("openTickets", openTickets);
            summary.put("url", "/vendor-tickets");
            summary.put("actionLabel", "View My Tickets");
        }

        return summary;
    }

    // ─── Helper Formatting & Sanitization ────────────────────────────────────────

    private Map<String, Object> sanitizeVendorInfo(AppUser vendor, boolean isStaff) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("found", true);
        data.put("companyName", vendor.getCompanyName() != null ? vendor.getCompanyName() : "N/A");
        data.put("vendorCode", vendor.getVendorCode() != null ? vendor.getVendorCode() : "N/A");
        data.put("verified", vendor.isVerified());
        data.put("verificationStatus", vendor.isVerified() ? "Verified" : "Pending Verification");
        data.put("registrationDate", vendor.getRegistrationDate() != null
                ? vendor.getRegistrationDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                : "N/A");
        if (isStaff) {
            data.put("url", "/admin/vendors/" + vendor.getId());
            data.put("actionLabel", "Open vendor profile");
        } else {
            data.put("url", "/profile");
            data.put("actionLabel", "View Profile");
        }
        // NEVER output vendor.getGstNumber() (NPWP), passwords, bank accounts, or raw docs!
        return data;
    }

    private String formatCurrency(BigDecimal amount, String currency) {
        if (amount == null) {
            return "Rp 0";
        }
        if (currency == null || "IDR".equalsIgnoreCase(currency)) {
            return FormatUtils.formatIDRFull(amount);
        }
        return currency + " " + amount.toPlainString();
    }

    private TicketStatus parseTicketStatus(String status) {
        if (status == null || status.isBlank()) return null;
        for (TicketStatus ts : TicketStatus.values()) {
            if (ts.name().equalsIgnoreCase(status.trim())) return ts;
        }
        return null;
    }

    /**
     * Sanitizes note text to prevent prompt injection or credential leakage
     */
    private String sanitizeNote(String note) {
        if (note == null) return "";
        // Clean and cap length
        String cleaned = note.trim();
        if (cleaned.length() > 500) {
            cleaned = cleaned.substring(0, 500) + "...";
        }
        return cleaned;
    }
}
