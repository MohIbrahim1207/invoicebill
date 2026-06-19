package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.VendorTicketWizardState;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.Client;
import com.billing.invoicehub.entity.Invoice;
import com.billing.invoicehub.entity.TicketStatus;
import com.billing.invoicehub.entity.VendorTicket;
import com.billing.invoicehub.entity.VendorTicketHistory;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.ClientRepository;
import com.billing.invoicehub.repository.InvoiceRepository;
import com.billing.invoicehub.repository.VendorTicketHistoryRepository;
import com.billing.invoicehub.repository.VendorTicketRepository;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendorTicketService {

    private static final Logger log = LoggerFactory.getLogger(VendorTicketService.class);

    private final InvoiceRepository invoiceRepository;
    private final VendorTicketRepository vendorTicketRepository;
    private final VendorTicketHistoryRepository vendorTicketHistoryRepository;
    private final AppUserRepository appUserRepository;
    private final ClientRepository clientRepository;
    private final EmailService emailService;
    private final String configuredAdminEmail;
    private final String appBaseUrl;
    private final PurchaseOrderService purchaseOrderService;

    public VendorTicketService(VendorTicketRepository vendorTicketRepository,
            VendorTicketHistoryRepository vendorTicketHistoryRepository,
            AppUserRepository appUserRepository,
            ClientRepository clientRepository,
            InvoiceRepository invoiceRepository,
            EmailService emailService,
            PurchaseOrderService purchaseOrderService,
            @Value("${app.admin.email:}") String configuredAdminEmail,
            @Value("${app.base-url:https://your-invoicehub.example.com}") String appBaseUrl) {
        this.vendorTicketRepository = vendorTicketRepository;
        this.vendorTicketHistoryRepository = vendorTicketHistoryRepository;
        this.appUserRepository = appUserRepository;
        this.clientRepository = clientRepository;
        this.invoiceRepository = invoiceRepository;
        this.emailService = emailService;
        this.configuredAdminEmail = configuredAdminEmail;
        this.appBaseUrl = appBaseUrl;
        this.purchaseOrderService = purchaseOrderService;
    }

    @Transactional(readOnly = true)
    public List<VendorTicket> searchTickets(String ticketNo, String invoiceNo, Integer year, String status) {
        return vendorTicketRepository.searchTickets(normalize(ticketNo), normalize(invoiceNo), year,
                parseStatus(status));
    }

    @Transactional(readOnly = true)
    public List<VendorTicket> searchTicketsForOwner(Long ownerId, String ticketNo, String invoiceNo, Integer year,
            String status) {
        return vendorTicketRepository.searchTicketsByOwner(ownerId, normalize(ticketNo), normalize(invoiceNo), year,
                parseStatus(status));
    }

    @Transactional(readOnly = true)
    public List<Integer> availableYears() {
        return vendorTicketRepository.findAvailableYears();
    }

    @Transactional(readOnly = true)
    public List<Integer> availableYearsForOwner(Long ownerId) {
        return vendorTicketRepository.findAvailableYearsByOwnerId(ownerId);
    }

    @Transactional(readOnly = true)
    public List<Client> getClientsForVendor(String vendorUsername) {
        Optional<AppUser> vendor = appUserRepository.findByUsername(vendorUsername);
        if (vendor.isEmpty()) {
            return List.of();
        }
        if (isAdmin(vendor.get())) {
            return clientRepository.findAll();
        }
        return clientRepository.findByOwner_Id(vendor.get().getId());
    }

    @Transactional(readOnly = true)
    public AppUser getVendorByUsername(String username) {
        return appUserRepository.findByUsername(username).orElse(null);
    }

    @Transactional(readOnly = true)
    public Optional<Client> resolveAccessibleClient(Long clientId, String username) {
        Optional<AppUser> user = appUserRepository.findByUsername(username);
        if (user.isEmpty()) {
            return Optional.empty();
        }
        AppUser currentUser = user.get();
        if (isAdmin(currentUser)) {
            return clientRepository.findById(clientId);
        }
        return clientRepository.findById(clientId)
                .filter(client -> client.getOwner() != null && client.getOwner().getId().equals(currentUser.getId()));
    }

    @Transactional(readOnly = true)
    public Optional<VendorTicket> getTicketById(Long ticketId) {
        return vendorTicketRepository.findDetailedById(ticketId);
    }

    @Transactional(readOnly = true)
    public Optional<VendorTicket> getAccessibleTicket(Long ticketId, String username) {
        log.debug("Loading accessible vendor ticket {} for username='{}'", ticketId, username);
        Optional<AppUser> user = appUserRepository.findByUsername(username);
        if (user.isEmpty()) {
            log.debug("No app user found for username='{}'", username);
            return Optional.empty();
        }
        AppUser currentUser = user.get();
        if (isAdmin(currentUser)) {
            return vendorTicketRepository.findDetailedById(ticketId);
        }
        return vendorTicketRepository.findDetailedById(ticketId)
                .filter(ticket -> ticket.getOwner() != null && ticket.getOwner().getId().equals(currentUser.getId()));
    }

    @Transactional(readOnly = true)
    public List<VendorTicketHistory> getTicketHistory(Long ticketId) {
        log.debug("Loading ticket history for ticketId={}", ticketId);
        List<VendorTicketHistory> history = vendorTicketHistoryRepository.findByTicketIdOrderByChangedAtDesc(ticketId);
        log.debug("Ticket history lookup returned {} row(s) for ticketId={}", history.size(), ticketId);
        return history;
    }

    @Transactional
    public VendorTicket saveWizardTicket(VendorTicketWizardState state, String vendorUsername) {
        AppUser vendor = appUserRepository.findByUsername(vendorUsername)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found"));
        if (state.getPoNumber() != null && !state.getPoNumber().trim().isEmpty()
                && !validatePONumber(state.getPoNumber())) {
            log.warn("PO number {} was not found or inactive for vendor {}. Continuing ticket submission.",
                    state.getPoNumber(), vendorUsername);
        }
        Client client = resolveSelfClient(vendor);
        VendorTicket vendorTicket = new VendorTicket();
        vendorTicket.setVendor(vendor);
        vendorTicket.setOwner(vendor);
        vendorTicket.setClient(client);
        vendorTicket.setInvoiceNo(state.getInvoiceNo());
        vendorTicket.setInvoiceDate(state.getInvoiceDate());
        vendorTicket.setAmount(state.getAmount());
        vendorTicket.setCurrency(state.getCurrency());
        vendorTicket.setInvoiceFileName(state.getInvoiceFileOriginalName());
        vendorTicket.setInvoiceFileUrl(state.getInvoiceFileUrl());
        vendorTicket.setSupportingDocumentName(state.getSupportingDocumentOriginalName());
        vendorTicket.setSupportingDocumentUrl(state.getSupportingDocumentUrl());
        vendorTicket.setTicketNo(state.getTicketNo());
        vendorTicket.setSubtotal(state.getSubtotal());
        BigDecimal taxVal = state.getTax() != null ? state.getTax() : java.math.BigDecimal.ZERO;
        vendorTicket.setTax(taxVal);
        vendorTicket.setTotal(state.getTotal());
        vendorTicket.setPoNumber(state.getPoNumber());
        vendorTicket.setTaxDocumentUrl(state.getTaxDocumentUrl());
        vendorTicket.setPoCopyUrl(state.getPoCopyUrl());
        vendorTicket.setDeliveryNoteUrl(state.getDeliveryNoteUrl());
        vendorTicket.setOtherDocumentUrl(state.getOtherDocumentUrl());
        vendorTicket.setStatusRequest(TicketStatus.OPEN);
        vendorTicket.setCreatedAt(LocalDateTime.now());
        VendorTicket saved = vendorTicketRepository.save(vendorTicket);
        vendorTicketHistoryRepository
                .save(new VendorTicketHistory(saved, TicketStatus.OPEN, LocalDateTime.now(), "Ticket submitted"));
        try {
            if (saved.getInvoiceFileUrl() != null && !saved.getInvoiceFileUrl().isBlank()) {
                Invoice inv = new Invoice();
                inv.setFileName(saved.getInvoiceFileName());
                inv.setFileUrl(saved.getInvoiceFileUrl());
                inv.setInvoiceNumber(saved.getInvoiceNo());
                inv.setInvoiceDate(saved.getInvoiceDate() != null ? saved.getInvoiceDate().toString() : null);
                // Use total as the final payable amount for Billing Intake; fallback to amount
                // if total is null
                java.math.BigDecimal billingAmount = (saved.getAmount() == null ? java.math.BigDecimal.ZERO
                        : saved.getAmount())
                        .add(saved.getSubtotal() == null ? java.math.BigDecimal.ZERO : saved.getSubtotal())
                        .add(saved.getTax() == null ? java.math.BigDecimal.ZERO : saved.getTax());

                inv.setAmount(billingAmount);
                inv.setClient(saved.getClient());
                inv.setCurrency(saved.getCurrency());
                invoiceRepository.save(inv);
                log.info("Created Invoice record {} for ticket {}", inv.getId(), saved.getTicketNo());
            }
        } catch (Exception ex) {
            log.warn("Failed to create Invoice record for ticket {}: {}", saved.getTicketNo(), ex.getMessage(), ex);
        }
        state.setTicketNo(saved.getTicketNo());
        String recipientEmail = saved.getVendor() != null ? saved.getVendor().getEmail() : null;
        String recipientName = saved.getVendor() != null ? saved.getVendor().getUsername() : "-";
        if (recipientEmail == null || recipientEmail.isBlank()) {
            recipientEmail = resolveAdminEmail();
        }
        if (recipientEmail != null && !recipientEmail.isBlank()) {
            String curr = saved.getCurrency() != null ? saved.getCurrency() : "IDR";
            emailService.sendTicketSubmittedEmail(
                    recipientEmail,
                    recipientName,
                    saved.getTicketNo(),
                    saved.getInvoiceNo(),
                    saved.getInvoiceDate() != null ? saved.getInvoiceDate().toString() : "-",
                    saved.getClient() != null ? saved.getClient().getCompanyName() : "-",
                    saved.getPoNumber(),
                    saved.getSubtotal() != null ? formatCurrency(saved.getSubtotal(), saved.getCurrency()) : curr + " 0",
                    saved.getTax() != null ? formatCurrency(saved.getTax(), saved.getCurrency()) : curr + " 0",
                    saved.getTotal() != null ? formatCurrency(saved.getTotal(), saved.getCurrency()) : curr + " 0",
                    appBaseUrl + "/vendor/tickets?historyTicketId=" + saved.getId() + "#ticket-history");
        }
        return saved;
    }

    private Client resolveSelfClient(AppUser vendor) {
        String billToName = vendor.getUsername();
        return clientRepository.findByCompanyNameIgnoreCaseAndOwner_Id(billToName, vendor.getId()).orElseGet(() -> {
            Client selfClient = new Client();
            selfClient.setCompanyName(billToName);
            selfClient.setOwner(vendor);
            return clientRepository.save(selfClient);
        });
    }

    @Transactional
    public void createVendorTicket(VendorTicket vendorTicket, Long clientId, String vendorUsername) {
        VendorTicketWizardState state = new VendorTicketWizardState();
        state.setClientId(clientId);
        state.setInvoiceNo(vendorTicket.getInvoiceNo());
        state.setInvoiceDate(vendorTicket.getInvoiceDate());
        state.setAmount(vendorTicket.getAmount());
        state.setCurrency(vendorTicket.getCurrency());
        saveWizardTicket(state, vendorUsername);
    }

    @Transactional
    public boolean cancelTicket(Long id) {
        Optional<VendorTicket> ticket = vendorTicketRepository.findById(id);
        if (ticket.isEmpty()) {
            return false;
        }
        ticket.get().setStatusRequest(TicketStatus.CANCEL);
        vendorTicketRepository.save(ticket.get());
        vendorTicketHistoryRepository.save(
                new VendorTicketHistory(ticket.get(), TicketStatus.CANCEL, LocalDateTime.now(), "Ticket cancelled"));
        return true;
    }

    @Transactional
    public boolean cancelTicket(Long id, String username) {
        log.info("Attempting to cancel ticket {} by user {}", id, username);
        Optional<VendorTicket> ticket = getAccessibleTicket(id, username);
        if (ticket.isEmpty()) {
            Optional<VendorTicket> maybe = vendorTicketRepository.findById(id);
            if (maybe.isPresent()) {
                VendorTicket t = maybe.get();
                String ownerName = t.getOwner() != null ? t.getOwner().getUsername() : "<no-owner>";
                log.warn("User {} attempted to cancel ticket {} but access denied. Ticket owner: {}", username, id,
                        ownerName);
            } else {
                log.warn("User {} attempted to cancel ticket {} but ticket not found", username, id);
            }
            return false;
        }
        VendorTicket t = ticket.get();
        t.setStatusRequest(TicketStatus.CANCEL);
        vendorTicketRepository.save(t);
        vendorTicketHistoryRepository
                .save(new VendorTicketHistory(t, TicketStatus.CANCEL, LocalDateTime.now(), "Ticket cancelled"));
        String recipientEmail = t.getVendor() != null ? t.getVendor().getEmail() : null;
        if (recipientEmail != null && !recipientEmail.isBlank()) {
            emailService.sendTicketCancelledEmail(
                    recipientEmail,
                    t.getVendor() != null ? t.getVendor().getUsername() : "-",
                    t.getTicketNo(),
                    t.getInvoiceNo(),
                    "Cancelled by user " + username);
        }
        log.info("Ticket {} cancelled by user {}", id, username);
        return true;
    }

    @Transactional
    public void updateTicket(VendorTicket ticket) {
        vendorTicketRepository.save(ticket);
    }

    @Transactional
    public void updateTicketStatusAndNotify(VendorTicket ticket, TicketStatus newStatus, String comment) {
        ticket.setStatusRequest(newStatus);
        vendorTicketRepository.save(ticket);
        vendorTicketHistoryRepository.save(new VendorTicketHistory(ticket, newStatus, LocalDateTime.now(),
                comment != null ? comment.trim() : ""));

        String mappedInvoiceStatus = mapTicketStatusToInvoiceStatus(newStatus);
        if (mappedInvoiceStatus == null) {
            return;
        }

        if (ticket.getInvoiceNo() != null && !ticket.getInvoiceNo().isBlank()
                && ticket.getOwner() != null && ticket.getOwner().getId() != null) {
            try {
                invoiceRepository
                        .findTopByInvoiceNumberAndClient_Owner_IdOrderByIdDesc(ticket.getInvoiceNo(),
                                ticket.getOwner().getId())
                        .ifPresent(invoice -> {
                            invoice.setStatus(mappedInvoiceStatus);
                            invoiceRepository.save(invoice);
                        });
            } catch (Exception ex) {
                log.warn("Failed to sync invoice status for ticket {}: {}", ticket.getTicketNo(), ex.getMessage(), ex);
            }
        }

        String vendorEmail = ticket.getVendor() != null ? ticket.getVendor().getEmail() : null;
        if (vendorEmail == null || vendorEmail.isBlank()) {
            log.warn("Skipping status email for ticket {} because vendor email is missing", ticket.getTicketNo());
            return;
        }

        try {
            String invoiceNumber = ticket.getInvoiceNo() != null ? ticket.getInvoiceNo() : "-";
            String vendorName = ticket.getVendor() != null ? ticket.getVendor().getUsername() : "-";
            String amount = ticket.getAmount() != null ? formatCurrency(ticket.getAmount(), ticket.getCurrency()) : (ticket.getCurrency() != null ? ticket.getCurrency() : "IDR") + " 0";
            String statusDate = LocalDateTime.now().toString();
            String adminRemarks = comment != null ? comment.trim() : null;
            String viewUrl = appBaseUrl + "/invoice";
            emailService.sendInvoiceStatusEmail(vendorEmail, invoiceNumber, vendorName, mappedInvoiceStatus, amount,
                    statusDate, adminRemarks, viewUrl);
            log.info("Invoice status email sent to {} for ticket {}", vendorEmail, ticket.getTicketNo());
        } catch (Exception ex) {
            log.error("Failed to send invoice status email for ticket {}: {}", ticket.getTicketNo(), ex.getMessage(),
                    ex);
        }
    }

    @Transactional
    public void addTicketHistory(VendorTicketHistory history) {
        vendorTicketHistoryRepository.save(history);
    }

    public String generateTicketNo() {
        int year = Year.now().getValue();
        LocalDateTime start = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime end = LocalDate.of(year + 1, 1, 1).atStartOfDay().minusNanos(1L);
        long sequence = vendorTicketRepository.countByCreatedAtBetween(start, end) + 1L;
        return year + "-" + String.format("%05d", sequence);
    }

    @Transactional(readOnly = true)
    public boolean validatePONumber(String poNumber) {
        if (poNumber == null || poNumber.trim().isEmpty()) {
            return false;
        }
        return purchaseOrderService.validatePO(poNumber);
    }

    private String formatCurrency(Number n, String currency) {
        if (n == null) {
            return "-";
        }
        String curr = (currency == null || currency.isBlank()) ? "IDR" : currency.trim();
        NumberFormat nf;
        if ("IDR".equalsIgnoreCase(curr)) {
            nf = NumberFormat.getInstance(Locale.forLanguageTag("id-ID"));
            nf.setMaximumFractionDigits(0);
            nf.setMinimumFractionDigits(0);
        } else {
            nf = NumberFormat.getInstance(Locale.US);
            nf.setMaximumFractionDigits(2);
            nf.setMinimumFractionDigits(2);
        }
        return curr + " " + nf.format(n);
    }

    private boolean isAdmin(AppUser user) {
        return user.getRoles().stream()
                .anyMatch(role -> role.getName() != null && role.getName().equalsIgnoreCase("ROLE_ADMIN"));
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String mapTicketStatusToInvoiceStatus(TicketStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case OPEN -> "Pending";
            case IN_PROGRESS -> "In Progress";
            case REVISE -> "Revised";
            case RESOLVED -> "Approved";
            case CANCEL -> "Rejected";
            default -> "Pending";
        };
    }

    private String resolveAdminEmail() {
        if (configuredAdminEmail != null && !configuredAdminEmail.isBlank()) {
            return configuredAdminEmail;
        }
        return appUserRepository.findAll().stream()
                .filter(u -> u.getRoles() != null && u.getRoles().stream()
                        .anyMatch(r -> r.getName() != null && r.getName().equalsIgnoreCase("ROLE_ADMIN")))
                .map(AppUser::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .findFirst()
                .orElse(null);
    }

    private TicketStatus parseStatus(String rawStatus) {
        String normalized = normalize(rawStatus);
        if (normalized == null || "ALL".equalsIgnoreCase(normalized)) {
            return null;
        }
        try {
            return TicketStatus.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
