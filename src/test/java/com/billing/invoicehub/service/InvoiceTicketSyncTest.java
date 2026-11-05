package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.VendorTicketWizardState;
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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    @SpyBean
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

    @Test
    @DisplayName("Admin can create and link invoice from unsynced ticket (like Ticket 45)")
    void testCreateAndLinkInvoiceFromTicket() {
        String invNum = "INV-UNSYNC-45-" + System.currentTimeMillis();

        VendorTicket ticket = new VendorTicket();
        ticket.setTicketNo("TCK-45-" + System.currentTimeMillis());
        ticket.setInvoiceNo(invNum);
        ticket.setVendor(vendor);
        ticket.setOwner(vendor);
        ticket.setClient(client);
        ticket.setSubtotal(new BigDecimal("1600000.00"));
        ticket.setTax(new BigDecimal("17600.00"));
        ticket.setTotal(new BigDecimal("1617600.00"));
        ticket.setInvoiceDate(LocalDate.of(2026, 10, 7));
        ticket.setInvoiceFileUrl("https://example.com/inv45.pdf");
        ticket.setInvoiceFileName("inv45.pdf");
        ticket.setStatusRequest(TicketStatus.IN_PROGRESS);
        ticket.setCreatedAt(LocalDateTime.now());
        ticket = vendorTicketRepository.save(ticket);

        assertNull(ticket.getInvoice(), "Initially unsynced ticket has null invoice");

        // Admin invokes createAndLinkInvoiceFromTicket
        Invoice created = vendorTicketService.createAndLinkInvoiceFromTicket(ticket.getId(), "super_admin");
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals(invNum, created.getInvoiceNumber());
        assertEquals(new BigDecimal("1617600.00"), created.getAmount());
        assertEquals("Pending", created.getStatus());

        // Refresh ticket
        VendorTicket refreshed = vendorTicketRepository.findById(ticket.getId()).orElseThrow();
        assertNotNull(refreshed.getInvoice(), "Ticket must now be linked to the newly created invoice");
        assertEquals(created.getId(), refreshed.getInvoice().getId());
    }

    @Test
    @DisplayName("Admin can link unsynced ticket to an existing invoice")
    void testLinkTicketToExistingInvoice() {
        String invNum = "INV-EXISTING-" + System.currentTimeMillis();

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(invNum);
        invoice.setInvoiceDate(LocalDate.of(2026, 10, 10));
        invoice.setAmount(new BigDecimal("2500000"));
        invoice.setClient(client);
        invoice.setStatus("Pending");
        invoice = invoiceRepository.save(invoice);

        VendorTicket ticket = new VendorTicket();
        ticket.setTicketNo("TCK-EXISTING-" + System.currentTimeMillis());
        ticket.setInvoiceNo(invNum);
        ticket.setVendor(vendor);
        ticket.setOwner(vendor);
        ticket.setClient(client);
        ticket.setAmount(new BigDecimal("2500000"));
        ticket.setStatusRequest(TicketStatus.OPEN);
        ticket.setCreatedAt(LocalDateTime.now());
        ticket = vendorTicketRepository.save(ticket);

        // Find candidate invoices
        List<Invoice> candidates = vendorTicketService.findCandidateInvoicesForTicket(ticket);
        assertFalse(candidates.isEmpty(), "Should find candidate invoice matching invoice number");
        assertEquals(invoice.getId(), candidates.get(0).getId());

        // Link ticket to invoice
        vendorTicketService.linkTicketToInvoice(ticket.getId(), invoice.getId(), "accounts_admin");

        VendorTicket refreshed = vendorTicketRepository.findById(ticket.getId()).orElseThrow();
        assertNotNull(refreshed.getInvoice());
        assertEquals(invoice.getId(), refreshed.getInvoice().getId());
    }

    @Test
    @DisplayName("Tax warning hint checks Indonesian PPN rates accurately")
    void testCalculateTaxWarningHint() {
        // Ticket 45 scenario: 1.1% deemed PPN on logistics (17,600 on 1,600,000) -> Valid, no warning
        VendorTicket ticket45 = new VendorTicket();
        ticket45.setSubtotal(new BigDecimal("1600000.00"));
        ticket45.setTax(new BigDecimal("17600.00"));
        assertNull(vendorTicketService.calculateTaxWarningHint(ticket45), "1.1% rate should not trigger warning hint");

        // 11% standard PPN (110,000 on 1,000,000) -> Valid, no warning
        VendorTicket ticket11 = new VendorTicket();
        ticket11.setSubtotal(new BigDecimal("1000000.00"));
        ticket11.setTax(new BigDecimal("110000.00"));
        assertNull(vendorTicketService.calculateTaxWarningHint(ticket11), "11% rate should not trigger warning hint");

        // 12% standard PPN (120,000 on 1,000,000) -> Valid, no warning
        VendorTicket ticket12 = new VendorTicket();
        ticket12.setSubtotal(new BigDecimal("1000000.00"));
        ticket12.setTax(new BigDecimal("120000.00"));
        assertNull(vendorTicketService.calculateTaxWarningHint(ticket12), "12% rate should not trigger warning hint");

        // Non-standard rate: 5% (50,000 on 1,000,000) -> Should return warning hint
        VendorTicket ticket5 = new VendorTicket();
        ticket5.setSubtotal(new BigDecimal("1000000.00"));
        ticket5.setTax(new BigDecimal("50000.00"));
        String hint = vendorTicketService.calculateTaxWarningHint(ticket5);
        assertNotNull(hint, "5% rate should trigger a tax notice hint");
        assertTrue(hint.contains("5.00%") || hint.contains("5,00%"));

        // Zero or null tax -> No warning
        VendorTicket ticketZero = new VendorTicket();
        ticketZero.setSubtotal(new BigDecimal("1000000.00"));
        ticketZero.setTax(BigDecimal.ZERO);
        assertNull(vendorTicketService.calculateTaxWarningHint(ticketZero));
    }

    @Test
    @DisplayName("Save wizard ticket rolls back atomically if invoice creation fails")
    void testAtomicTicketAndInvoiceCreationRollback() {
        VendorTicketWizardState state = new VendorTicketWizardState();
        state.setClientId(client.getId());
        state.setClientName(client.getCompanyName());
        state.setInvoiceNo("INV-FAIL-" + System.currentTimeMillis());
        state.setAmount(new BigDecimal("1500000"));
        state.setSubtotal(new BigDecimal("1500000"));
        state.setTax(BigDecimal.ZERO);
        state.setTotal(new BigDecimal("1500000"));
        state.setInvoiceDate(LocalDate.now());
        state.setInvoiceFileUrl("https://cloudinary.com/test-invoice.pdf");
        state.setInvoiceFileName("test-invoice.pdf");

        // Force invoiceRepository.save to throw an exception when saving Invoice
        doThrow(new RuntimeException("Simulated invoice database error"))
                .when(invoiceRepository).save(any(Invoice.class));

        try {
            assertThrows(IllegalStateException.class, () -> {
                vendorTicketService.saveWizardTicket(state, vendor.getUsername());
            });

            // Verify that ticket was NOT saved into database (rolled back)
            assertTrue(vendorTicketRepository.findAll().stream()
                    .noneMatch(t -> state.getInvoiceNo().equals(t.getInvoiceNo())),
                    "Ticket must not exist in DB due to atomic rollback");
        } finally {
            reset(invoiceRepository);
        }
    }
}
