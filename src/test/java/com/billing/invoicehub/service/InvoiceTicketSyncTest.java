package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.AppRole;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.Client;
import com.billing.invoicehub.entity.Invoice;
import com.billing.invoicehub.entity.TicketStatus;
import com.billing.invoicehub.entity.VendorTicket;
import com.billing.invoicehub.repository.AppRoleRepository;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.ClientRepository;
import com.billing.invoicehub.repository.InvoiceRepository;
import com.billing.invoicehub.repository.VendorTicketHistoryRepository;
import com.billing.invoicehub.repository.VendorTicketRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "rate-limiting.enabled=false"
})
class InvoiceTicketSyncTest {

    @Autowired
    private VendorTicketService vendorTicketService;

    @Autowired
    private StartupRoleMigration startupRoleMigration;

    @Autowired
    private VendorTicketRepository vendorTicketRepository;

    @Autowired
    private VendorTicketHistoryRepository vendorTicketHistoryRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AppRoleRepository appRoleRepository;

    @MockBean
    private EmailService emailService;

    private AppUser vendor;
    private Client client;

    @BeforeEach
    void setup() {
        AppRole vendorRole = appRoleRepository.findByName("ROLE_VENDOR")
                .orElseGet(() -> {
                    AppRole r = new AppRole();
                    r.setName("ROLE_VENDOR");
                    return appRoleRepository.save(r);
                });

        vendor = appUserRepository.findFirstByUsernameAndUserDomain("sync_fk_vendor", "VENDOR")
                .orElseGet(() -> {
                    AppUser u = new AppUser();
                    u.setUsername("sync_fk_vendor");
                    u.setEmail("sync_fk_vendor@test.com");
                    u.setPassword("Password123!@#");
                    u.setUserDomain("VENDOR");
                    u.setEnabled(true);
                    u.setRoles(Set.of(vendorRole));
                    return appUserRepository.save(u);
                });

        client = clientRepository.findByCompanyNameIgnoreCaseAndOwner_Id("SyncFKClient", vendor.getId())
                .orElseGet(() -> {
                    Client c = new Client();
                    c.setCompanyName("SyncFKClient");
                    c.setOwner(vendor);
                    return clientRepository.save(c);
                });
    }

    @AfterEach
    void tearDown() {
        vendorTicketHistoryRepository.deleteAll();
        vendorTicketRepository.deleteAll();
        invoiceRepository.deleteAll();
    }

    @Test
    @DisplayName("Verify Invoice invoiceDate is LocalDate, raw text preserved, and only vendor_ticket.invoice_id FK is maintained")
    void testInvoiceDateAndSingleForeignKeyLinkage() {
        String invNum = "INV-FK-" + System.currentTimeMillis();
        LocalDate testDate = LocalDate.of(2026, 10, 31);

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(invNum);
        invoice.setInvoiceDate(testDate);
        invoice.setInvoiceDateRaw("31/10/2026");
        invoice.setAmount(new BigDecimal("7500000"));
        invoice.setClient(client);
        invoice.setStatus("Pending");
        invoice = invoiceRepository.save(invoice);

        VendorTicket ticket = new VendorTicket();
        ticket.setTicketNo("TCK-FK-" + System.currentTimeMillis());
        ticket.setInvoiceNo(invNum);
        ticket.setVendor(vendor);
        ticket.setOwner(vendor);
        ticket.setClient(client);
        ticket.setAmount(new BigDecimal("7500000"));
        ticket.setStatusRequest(TicketStatus.OPEN);
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setInvoice(invoice);
        ticket = vendorTicketRepository.save(ticket);

        // Assertions: FK exists on vendor_ticket pointing to invoice
        VendorTicket fetchedTicket = vendorTicketRepository.findById(ticket.getId()).orElseThrow();
        assertNotNull(fetchedTicket.getInvoice());
        assertEquals(invoice.getId(), fetchedTicket.getInvoice().getId());

        Invoice fetchedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(testDate, fetchedInvoice.getInvoiceDate());
        assertEquals("31/10/2026", fetchedInvoice.getInvoiceDateRaw());
    }

    @Test
    @DisplayName("Flexible date parsing supports dd/MM/yyyy, dd-MM-yyyy, and yyyy-MM-dd")
    void testFlexibleDateParsing() {
        assertEquals(LocalDate.of(2026, 10, 31), StartupRoleMigration.parseFlexibleDate("31/10/2026"));
        assertEquals(LocalDate.of(2026, 10, 31), StartupRoleMigration.parseFlexibleDate("31-10-2026"));
        assertEquals(LocalDate.of(2026, 10, 31), StartupRoleMigration.parseFlexibleDate("2026-10-31"));
        assertEquals(LocalDate.of(2026, 5, 1), StartupRoleMigration.parseFlexibleDate("1/5/2026"));
        assertNull(StartupRoleMigration.parseFlexibleDate("not-a-date"));
        assertNull(StartupRoleMigration.parseFlexibleDate(null));
        assertNull(StartupRoleMigration.parseFlexibleDate(""));
    }

    @Test
    @DisplayName("Status update propagates through vendor_ticket.invoice foreign key to Invoice status")
    void testStatusUpdatePropagatesViaForeignKey() {
        String invNum = "INV-SYNC-FK-" + System.currentTimeMillis();

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(invNum);
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setAmount(new BigDecimal("12000000"));
        invoice.setClient(client);
        invoice.setStatus("Pending");
        invoice = invoiceRepository.save(invoice);

        VendorTicket ticket = new VendorTicket();
        ticket.setTicketNo("TCK-SYNC-FK-" + System.currentTimeMillis());
        ticket.setInvoiceNo(invNum);
        ticket.setVendor(vendor);
        ticket.setOwner(vendor);
        ticket.setClient(client);
        ticket.setAmount(new BigDecimal("12000000"));
        ticket.setStatusRequest(TicketStatus.OPEN);
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setInvoice(invoice);
        ticket = vendorTicketRepository.save(ticket);

        // Transition ticket to RESOLVED (maps to Approved in Invoice)
        vendorTicketService.updateTicketStatusAndNotify(ticket, TicketStatus.RESOLVED, "Payment released");

        Invoice updatedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals("Approved", updatedInvoice.getStatus());
    }

    @Test
    @DisplayName("StartupRoleMigration backfills unlinked tickets to invoices correctly without guessing")
    void testBackfillLinksUnlinkedEntities() {
        String invNum = "INV-BACKFILL-" + System.currentTimeMillis();

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(invNum);
        invoice.setInvoiceDate(LocalDate.of(2026, 10, 25));
        invoice.setAmount(new BigDecimal("3500000"));
        invoice.setClient(client);
        invoice.setStatus("Pending");
        invoice = invoiceRepository.save(invoice);

        VendorTicket ticket = new VendorTicket();
        ticket.setTicketNo("TCK-BACKFILL-" + System.currentTimeMillis());
        ticket.setInvoiceNo(invNum);
        ticket.setVendor(vendor);
        ticket.setOwner(vendor);
        ticket.setClient(client);
        ticket.setAmount(new BigDecimal("3500000"));
        ticket.setStatusRequest(TicketStatus.OPEN);
        ticket.setCreatedAt(LocalDateTime.now());
        ticket = vendorTicketRepository.save(ticket);

        // Run backfill logic
        startupRoleMigration.backfillVendorTicketsAndInvoices();

        // Verify that ticket has foreign key to invoice
        VendorTicket refreshedTicket = vendorTicketRepository.findById(ticket.getId()).orElseThrow();
        assertNotNull(refreshedTicket.getInvoice(), "Ticket should now have foreign key linked to invoice");
        assertEquals(invoice.getId(), refreshedTicket.getInvoice().getId());
    }
}
