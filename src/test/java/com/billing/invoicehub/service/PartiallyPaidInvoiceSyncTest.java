package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.AppRole;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.Client;
import com.billing.invoicehub.entity.Invoice;
import com.billing.invoicehub.entity.TicketStatus;
import com.billing.invoicehub.entity.VendorTicket;
import com.billing.invoicehub.entity.VendorTicketHistory;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
@TestPropertySource(properties = {
        "rate-limiting.enabled=false"
})
class PartiallyPaidInvoiceSyncTest {

    @Autowired
    private VendorTicketService vendorTicketService;

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

    private VendorTicket ticket;
    private Invoice invoice;

    @BeforeEach
    void setup() {
        AppRole vendorRole = appRoleRepository.findByName("ROLE_VENDOR")
                .orElseGet(() -> {
                    AppRole r = new AppRole();
                    r.setName("ROLE_VENDOR");
                    return appRoleRepository.save(r);
                });

        AppUser vendor = appUserRepository.findFirstByUsernameAndUserDomain("sync_test_vendor", "VENDOR")
                .orElseGet(() -> {
                    AppUser u = new AppUser();
                    u.setUsername("sync_test_vendor");
                    u.setEmail("sync_vendor@test.com");
                    u.setPassword("password");
                    u.setUserDomain("VENDOR");
                    u.setEnabled(true);
                    u.setRoles(Set.of(vendorRole));
                    return appUserRepository.save(u);
                });

        Client client = clientRepository.findByCompanyNameIgnoreCaseAndOwner_Id("SyncClient", vendor.getId())
                .orElseGet(() -> {
                    Client c = new Client();
                    c.setCompanyName("SyncClient");
                    c.setOwner(vendor);
                    return clientRepository.save(c);
                });

        String invNum = "INV-SYNC-" + System.currentTimeMillis();

        invoice = new Invoice();
        invoice.setInvoiceNumber(invNum);
        invoice.setAmount(new BigDecimal("5000000"));
        invoice.setClient(client);
        invoice.setStatus("Pending");
        invoice = invoiceRepository.save(invoice);

        ticket = new VendorTicket();
        ticket.setTicketNo("TCK-SYNC-" + System.currentTimeMillis());
        ticket.setInvoiceNo(invNum);
        ticket.setVendor(vendor);
        ticket.setOwner(vendor);
        ticket.setClient(client);
        ticket.setAmount(new BigDecimal("5000000"));
        ticket.setStatusRequest(TicketStatus.OPEN);
        ticket.setCreatedAt(LocalDateTime.now());
        ticket = vendorTicketRepository.save(ticket);
    }

    @Test
    @DisplayName("PARTIALLY_PAID ticket status must sync invoice status to 'Partially Paid' and not 'Pending'")
    void updatingTicketToPartiallyPaidSyncsInvoiceToPartiallyPaid() {
        vendorTicketService.updateTicketStatusAndNotify(ticket, TicketStatus.PARTIALLY_PAID, "First installment paid");

        // Verify linked invoice in database
        Invoice updatedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals("Partially Paid", updatedInvoice.getStatus(),
                "Invoice status should be 'Partially Paid', but was: " + updatedInvoice.getStatus());

        // Verify email notification sent to vendor contains 'Partially Paid' as status
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendInvoiceStatusEmail(
                eq("sync_vendor@test.com"),
                eq(ticket.getInvoiceNo()),
                anyString(),
                statusCaptor.capture(),
                anyString(),
                anyString(),
                eq("First installment paid"),
                anyString()
        );

        assertEquals("Partially Paid", statusCaptor.getValue(),
                "Vendor email notification should have newStatus='Partially Paid', but was: " + statusCaptor.getValue());
    }

    @AfterEach
    void tearDown() {
        if (ticket != null && ticket.getId() != null) {
            List<VendorTicketHistory> histories = vendorTicketHistoryRepository.findByTicketIdOrderByChangedAtDesc(ticket.getId());
            vendorTicketHistoryRepository.deleteAll(histories);
            vendorTicketRepository.deleteById(ticket.getId());
        }
        if (invoice != null && invoice.getId() != null) {
            invoiceRepository.deleteById(invoice.getId());
        }
    }
}
