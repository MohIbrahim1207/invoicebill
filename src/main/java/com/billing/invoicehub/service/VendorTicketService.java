/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.dto.VendorTicketWizardState
 *  com.billing.invoicehub.entity.AppUser
 *  com.billing.invoicehub.entity.Client
 *  com.billing.invoicehub.entity.Invoice
 *  com.billing.invoicehub.entity.TicketStatus
 *  com.billing.invoicehub.entity.VendorTicket
 *  com.billing.invoicehub.entity.VendorTicketHistory
 *  com.billing.invoicehub.repository.AppUserRepository
 *  com.billing.invoicehub.repository.ClientRepository
 *  com.billing.invoicehub.repository.InvoiceRepository
 *  com.billing.invoicehub.repository.VendorTicketHistoryRepository
 *  com.billing.invoicehub.repository.VendorTicketRepository
 *  com.billing.invoicehub.service.PurchaseOrderService
 *  com.billing.invoicehub.service.VendorTicketService
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.annotation.Value
 *  org.springframework.mail.SimpleMailMessage
 *  org.springframework.mail.javamail.JavaMailSender
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 */
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
import com.billing.invoicehub.service.PurchaseOrderService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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
    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String configuredAdminEmail;
    private final PurchaseOrderService purchaseOrderService;

    public VendorTicketService(VendorTicketRepository vendorTicketRepository, VendorTicketHistoryRepository vendorTicketHistoryRepository, AppUserRepository appUserRepository, ClientRepository clientRepository, InvoiceRepository invoiceRepository, JavaMailSender mailSender, PurchaseOrderService purchaseOrderService, @Value(value="${spring.mail.username:}") String mailFrom, @Value(value="${app.admin.email:}") String configuredAdminEmail) {
        this.vendorTicketRepository = vendorTicketRepository;
        this.vendorTicketHistoryRepository = vendorTicketHistoryRepository;
        this.appUserRepository = appUserRepository;
        this.clientRepository = clientRepository;
        this.invoiceRepository = invoiceRepository;
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.configuredAdminEmail = configuredAdminEmail;
        this.purchaseOrderService = purchaseOrderService;
    }

    @Transactional(readOnly=true)
    public List<VendorTicket> searchTickets(String ticketNo, String invoiceNo, Integer year, String status) {
        return this.vendorTicketRepository.searchTickets(this.normalize(ticketNo), this.normalize(invoiceNo), year, this.parseStatus(status));
    }

    @Transactional(readOnly=true)
    public List<VendorTicket> searchTicketsForOwner(Long ownerId, String ticketNo, String invoiceNo, Integer year, String status) {
        return this.vendorTicketRepository.searchTicketsByOwner(ownerId, this.normalize(ticketNo), this.normalize(invoiceNo), year, this.parseStatus(status));
    }

    @Transactional(readOnly=true)
    public List<Integer> availableYears() {
        return this.vendorTicketRepository.findAvailableYears();
    }

    @Transactional(readOnly=true)
    public List<Integer> availableYearsForOwner(Long ownerId) {
        return this.vendorTicketRepository.findAvailableYearsByOwnerId(ownerId);
    }

    @Transactional(readOnly=true)
    public List<Client> getClientsForVendor(String vendorUsername) {
        Optional<AppUser> vendor = this.appUserRepository.findByUsername(vendorUsername);
        if (vendor.isEmpty()) {
            return List.of();
        }
        if (this.isAdmin(vendor.get())) {
            return this.clientRepository.findAll();
        }
        return this.clientRepository.findByOwner_Id(vendor.get().getId());
    }

    @Transactional(readOnly=true)
    public AppUser getVendorByUsername(String username) {
        return this.appUserRepository.findByUsername(username).orElse(null);
    }

    @Transactional(readOnly=true)
    public Optional<Client> resolveAccessibleClient(Long clientId, String username) {
        Optional<AppUser> user = this.appUserRepository.findByUsername(username);
        if (user.isEmpty()) {
            return Optional.empty();
        }
        AppUser currentUser = user.get();
        boolean admin = this.isAdmin(currentUser);
        if (admin) {
            return this.clientRepository.findById(clientId);
        }
        return this.clientRepository.findById(clientId).filter(client -> client.getOwner() != null && client.getOwner().getId().equals(currentUser.getId()));
    }

    @Transactional(readOnly=true)
    public Optional<VendorTicket> getTicketById(Long ticketId) {
        return this.vendorTicketRepository.findById(ticketId);
    }

    @Transactional(readOnly=true)
    public Optional<VendorTicket> getAccessibleTicket(Long ticketId, String username) {
        Optional<AppUser> user = this.appUserRepository.findByUsername(username);
        if (user.isEmpty()) {
            return Optional.empty();
        }
        AppUser currentUser = user.get();
        boolean admin = this.isAdmin(currentUser);
        if (admin) {
            return this.vendorTicketRepository.findById(ticketId);
        }
        return this.vendorTicketRepository.findById(ticketId).filter(ticket -> ticket.getOwner() != null && ticket.getOwner().getId().equals(currentUser.getId()));
    }

    @Transactional(readOnly=true)
    public List<VendorTicketHistory> getTicketHistory(Long ticketId) {
        return this.vendorTicketHistoryRepository.findByTicketIdOrderByChangedAtDesc(ticketId);
    }

    @Transactional
    public VendorTicket saveWizardTicket(VendorTicketWizardState state, String vendorUsername) {
        AppUser vendor = this.appUserRepository.findByUsername(vendorUsername).orElseThrow(() -> new IllegalArgumentException("Vendor not found"));
        if (state.getPoNumber() != null && !state.getPoNumber().trim().isEmpty() && !this.validatePONumber(state.getPoNumber())) {
            log.warn("PO number {} was not found or inactive for vendor {}. Continuing ticket submission.", (Object)state.getPoNumber(), (Object)vendorUsername);
        }
        Client client = this.resolveSelfClient(vendor);
        VendorTicket vendorTicket = new VendorTicket();
        vendorTicket.setVendor(vendor);
        vendorTicket.setOwner(vendor);
        vendorTicket.setClient(client);
        vendorTicket.setInvoiceNo(state.getInvoiceNo());
        vendorTicket.setInvoiceDate(state.getInvoiceDate());
        vendorTicket.setAmount(state.getAmount());
        vendorTicket.setCurrency(state.getCurrency());
        vendorTicket.setInvoiceFileName(state.getInvoiceFileName());
        vendorTicket.setSupportingDocumentName(state.getSupportingDocumentName());
        vendorTicket.setTicketNo(state.getTicketNo());
        vendorTicket.setSubtotal(state.getSubtotal());
        vendorTicket.setTax(state.getTax());
        vendorTicket.setTotal(state.getTotal());
        vendorTicket.setPoNumber(state.getPoNumber());
        vendorTicket.setTaxDocumentPath(state.getTaxDocumentName());
        vendorTicket.setPoCopyPath(state.getPoCopyName());
        vendorTicket.setDeliveryNotePath(state.getDeliveryNoteName());
        vendorTicket.setOtherDocumentPath(state.getOtherDocumentName());
        vendorTicket.setStatusRequest(TicketStatus.OPEN);
        vendorTicket.setCreatedAt(LocalDateTime.now());
        VendorTicket saved = this.vendorTicketRepository.save(vendorTicket);
        this.vendorTicketHistoryRepository.save(new VendorTicketHistory(saved, TicketStatus.OPEN, LocalDateTime.now(), "Ticket submitted"));
        try {
            if (saved.getInvoiceFileName() != null && !saved.getInvoiceFileName().isBlank()) {
                Invoice inv = new Invoice();
                inv.setFileName(saved.getInvoiceFileName());
                inv.setInvoiceNumber(saved.getInvoiceNo() != null ? saved.getInvoiceNo() : null);
                inv.setInvoiceDate(saved.getInvoiceDate() != null ? saved.getInvoiceDate().toString() : null);
                inv.setAmount(saved.getAmount());
                inv.setClient(saved.getClient());
                this.invoiceRepository.save(inv);
                log.info("Created Invoice record {} for ticket {}", inv.getId(), saved.getTicketNo());
            }
        }
        catch (Exception ex) {
            log.warn("Failed to create Invoice record for ticket {}: {}", new Object[]{saved.getTicketNo(), ex.getMessage(), ex});
        }
        state.setTicketNo(saved.getTicketNo());
        try {
            String vendorEmail;
            String string = vendorEmail = saved.getVendor() != null ? saved.getVendor().getEmail() : null;
            if (vendorEmail != null && !vendorEmail.isBlank()) {
                try {
                    SimpleMailMessage msg = new SimpleMailMessage();
                    msg.setTo(vendorEmail);
                    msg.setSubject("Invoice Submission Received - Ticket #" + saved.getTicketNo());
                    String body = String.format("Your invoice submission has been received.\n\n==== TICKET DETAILS ====\nTicket No: %s\nInvoice No: %s\nInvoice Date: %s\nBill To: %s\nPO Number: %s\n\n==== AMOUNT BREAKDOWN ====\nSubtotal: %s\nTax: %s\nTotal: %s %s\n\n==== SUBMISSION INFO ====\nSubmitted At: %s\nStatus: OPEN\n\nYou can view and track this ticket in the InvoiceHub portal.\n\nHelp & Support:\nHelpline: +1-800-123-4567\nFAQ: %s/contact or %s/faq\nContact Form: %s/contact", saved.getTicketNo(), saved.getInvoiceNo() != null ? saved.getInvoiceNo() : "-", saved.getInvoiceDate() != null ? saved.getInvoiceDate().toString() : "-", saved.getClient() != null ? saved.getClient().getCompanyName() : "-", saved.getPoNumber() != null ? saved.getPoNumber() : "-", saved.getSubtotal() != null ? saved.getSubtotal().toString() : "0.00", saved.getTax() != null ? saved.getTax().toString() : "0.00", saved.getTotal() != null ? saved.getTotal().toString() : "-", saved.getCurrency() != null ? saved.getCurrency() : "IDR", LocalDateTime.now().toString(), "https://your-invoicehub.example.com", "https://your-invoicehub.example.com", "https://your-invoicehub.example.com");
                    msg.setText(body);
                    if (this.mailFrom != null && !this.mailFrom.isBlank()) {
                        msg.setFrom(this.mailFrom);
                    }
                    this.mailSender.send(msg);
                    log.info("Vendor notification email sent to {} for ticket {}", (Object)vendorEmail, (Object)saved.getTicketNo());
                }
                catch (Exception mailEx) {
                    log.error("Failed to send vendor notification email to {}: {}", new Object[]{vendorEmail, mailEx.getMessage(), mailEx});
                }
            } else {
                String targetAdmin;
                String string2 = targetAdmin = this.configuredAdminEmail != null && !this.configuredAdminEmail.isBlank() ? this.configuredAdminEmail : null;
                if (targetAdmin != null) {
                    try {
                        SimpleMailMessage msg = new SimpleMailMessage();
                        msg.setTo(targetAdmin);
                        msg.setSubject("New Invoice Submitted - Ticket #" + saved.getTicketNo());
                        String body = String.format("A new invoice has been submitted for review.\n\n==== TICKET DETAILS ====\nTicket No: %s\nVendor: %s\nInvoice No: %s\nInvoice Date: %s\nBill To: %s\nPO Number: %s\n\n==== AMOUNT BREAKDOWN ====\nSubtotal: %s\nTax: %s\nTotal: %s %s\n\n==== SUBMISSION INFO ====\nSubmitted At: %s\nStatus: OPEN\n\nPlease login to InvoiceHub admin portal to review and process this submission.\n\nHelp & Support:\nHelpline: +1-800-123-4567\nFAQ: %s/faq\nContact Form: %s/contact", saved.getTicketNo(), saved.getVendor() != null ? saved.getVendor().getUsername() : "-", saved.getInvoiceNo() != null ? saved.getInvoiceNo() : "-", saved.getInvoiceDate() != null ? saved.getInvoiceDate().toString() : "-", saved.getClient() != null ? saved.getClient().getCompanyName() : "-", saved.getPoNumber() != null ? saved.getPoNumber() : "-", saved.getSubtotal() != null ? saved.getSubtotal().toString() : "0.00", saved.getTax() != null ? saved.getTax().toString() : "0.00", saved.getTotal() != null ? saved.getTotal().toString() : "-", saved.getCurrency() != null ? saved.getCurrency() : "IDR", LocalDateTime.now().toString(), "https://your-invoicehub.example.com", "https://your-invoicehub.example.com");
                        msg.setText(body);
                        if (this.mailFrom != null && !this.mailFrom.isBlank()) {
                            msg.setFrom(this.mailFrom);
                        }
                        this.mailSender.send(msg);
                        log.info("Admin notification email sent to configured admin {} for ticket {}", (Object)targetAdmin, (Object)saved.getTicketNo());
                    }
                    catch (Exception mailEx) {
                        log.error("Failed to send admin notification email to configured admin {}: {}", new Object[]{targetAdmin, mailEx.getMessage(), mailEx});
                    }
                } else {
                    this.appUserRepository.findAll().stream().filter(u -> u.getRoles() != null && u.getRoles().stream().anyMatch(r -> r.getName() != null && r.getName().equalsIgnoreCase("ROLE_ADMIN"))).findFirst().ifPresent(admin -> {
                        String adminEmail = admin.getEmail();
                        if (adminEmail != null && !adminEmail.isBlank()) {
                            try {
                                SimpleMailMessage msg = new SimpleMailMessage();
                                msg.setTo(adminEmail);
                                msg.setSubject("New Invoice Submitted - Ticket #" + saved.getTicketNo());
                                String body = String.format("A new invoice has been submitted for review.\n\n==== TICKET DETAILS ====\nTicket No: %s\nVendor: %s\nInvoice No: %s\nInvoice Date: %s\nBill To: %s\nPO Number: %s\n\n==== AMOUNT BREAKDOWN ====\nSubtotal: %s\nTax: %s\nTotal: %s %s\n\n==== SUBMISSION INFO ====\nSubmitted At: %s\nStatus: OPEN\n\nPlease login to InvoiceHub admin portal to review and process this submission.\n\nHelp & Support:\nHelpline: +1-800-123-4567\nFAQ: %s/faq\nContact Form: %s/contact", saved.getTicketNo(), saved.getVendor() != null ? saved.getVendor().getUsername() : "-", saved.getInvoiceNo() != null ? saved.getInvoiceNo() : "-", saved.getInvoiceDate() != null ? saved.getInvoiceDate().toString() : "-", saved.getClient() != null ? saved.getClient().getCompanyName() : "-", saved.getPoNumber() != null ? saved.getPoNumber() : "-", saved.getSubtotal() != null ? saved.getSubtotal().toString() : "0.00", saved.getTax() != null ? saved.getTax().toString() : "0.00", saved.getTotal() != null ? saved.getTotal().toString() : "-", saved.getCurrency() != null ? saved.getCurrency() : "IDR", LocalDateTime.now().toString(), "https://your-invoicehub.example.com", "https://your-invoicehub.example.com");
                                msg.setText(body);
                                if (this.mailFrom != null && !this.mailFrom.isBlank()) {
                                    msg.setFrom(this.mailFrom);
                                }
                                this.mailSender.send(msg);
                                log.info("Admin notification email sent to {} for ticket {}", (Object)adminEmail, (Object)saved.getTicketNo());
                            }
                            catch (Exception mailEx) {
                                log.error("Failed to send admin notification email to {}: {}", new Object[]{admin.getEmail(), mailEx.getMessage(), mailEx});
                            }
                        }
                    });
                }
            }
        }
        catch (Exception ex) {
            log.error("Failed to prepare or send notification email: {}", (Object)ex.getMessage(), (Object)ex);
        }
        return saved;
    }

    private Client resolveSelfClient(AppUser vendor) {
        String billToName = vendor.getUsername();
        return this.clientRepository.findByCompanyNameIgnoreCaseAndOwner_Id(billToName, vendor.getId()).orElseGet(() -> {
            Client selfClient = new Client();
            selfClient.setCompanyName(billToName);
            selfClient.setOwner(vendor);
            return this.clientRepository.save(selfClient);
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
        this.saveWizardTicket(state, vendorUsername);
    }

    @Transactional
    public boolean cancelTicket(Long id) {
        Optional<VendorTicket> ticket = this.vendorTicketRepository.findById(id);
        if (ticket.isEmpty()) {
            return false;
        }
        ticket.get().setStatusRequest(TicketStatus.CANCEL);
        this.vendorTicketRepository.save(ticket.get());
        this.vendorTicketHistoryRepository.save(new VendorTicketHistory(ticket.get(), TicketStatus.CANCEL, LocalDateTime.now(), "Ticket cancelled"));
        return true;
    }

    @Transactional
    public boolean cancelTicket(Long id, String username) {
        log.info("Attempting to cancel ticket {} by user {}", (Object)id, (Object)username);
        Optional<VendorTicket> ticket = this.getAccessibleTicket(id, username);
        if (ticket.isEmpty()) {
            Optional<VendorTicket> maybe = this.vendorTicketRepository.findById(id);
            if (maybe.isPresent()) {
                VendorTicket t = maybe.get();
                String ownerName = t.getOwner() != null ? t.getOwner().getUsername() : "<no-owner>";
                log.warn("User {} attempted to cancel ticket {} but access denied. Ticket owner: {}", username, id, ownerName);
            } else {
                log.warn("User {} attempted to cancel ticket {} but ticket not found", username, id);
            }
            return false;
        }
        VendorTicket t = ticket.get();
        t.setStatusRequest(TicketStatus.CANCEL);
        this.vendorTicketRepository.save(t);
        this.vendorTicketHistoryRepository.save(new VendorTicketHistory(t, TicketStatus.CANCEL, LocalDateTime.now(), "Ticket cancelled"));
        log.info("Ticket {} cancelled by user {}", id, username);
        return true;
    }

    @Transactional
    public void updateTicket(VendorTicket ticket) {
        this.vendorTicketRepository.save(ticket);
    }

    @Transactional
    public void addTicketHistory(VendorTicketHistory history) {
        this.vendorTicketHistoryRepository.save(history);
    }

    public String generateTicketNo() {
        int year = Year.now().getValue();
        LocalDateTime start = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime end = LocalDate.of(year + 1, 1, 1).atStartOfDay().minusNanos(1L);
        long sequence = this.vendorTicketRepository.countByCreatedAtBetween(start, end) + 1L;
        return year + "-" + String.format("%05d", sequence);
    }

    private boolean isAdmin(AppUser user) {
        return user.getRoles().stream().anyMatch(role -> role.getName() != null && role.getName().equalsIgnoreCase("ROLE_ADMIN"));
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private TicketStatus parseStatus(String rawStatus) {
        String normalized = this.normalize(rawStatus);
        if (normalized == null || "ALL".equalsIgnoreCase(normalized)) {
            return null;
        }
        try {
            return TicketStatus.valueOf((String)normalized.toUpperCase());
        }
        catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Transactional(readOnly=true)
    public boolean validatePONumber(String poNumber) {
        if (poNumber == null || poNumber.trim().isEmpty()) {
            return false;
        }
        return this.purchaseOrderService.validatePO(poNumber);
    }
}

