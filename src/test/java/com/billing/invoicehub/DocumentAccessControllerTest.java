package com.billing.invoicehub;

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
import com.billing.invoicehub.repository.VendorTicketRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limiting.enabled=false"
})
class DocumentAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AppRoleRepository roleRepository;

    @Autowired
    private VendorTicketRepository ticketRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private ClientRepository clientRepository;

    private AppUser vendor1;
    private AppUser vendor2;
    private VendorTicket ticket1;
    private VendorTicket ticket2;
    private Invoice invoice1;
    private Invoice invoice2;

    @BeforeEach
    void setUp() {
        AppRole roleVendor = roleRepository.findByName("ROLE_VENDOR")
                .orElseGet(() -> {
                    AppRole r = new AppRole();
                    r.setName("ROLE_VENDOR");
                    return roleRepository.save(r);
                });

        vendor1 = userRepository.findByUsername("doc_vendor_one").orElseGet(() -> {
            AppUser u = new AppUser();
            u.setUsername("doc_vendor_one");
            u.setEmail("doc1@vendor.test");
            u.setPassword("ValidPass123!@#");
            u.setUserDomain("VENDOR");
            u.setRoles(Set.of(roleVendor));
            u.setGstDocumentUrl("https://res.cloudinary.com/test/image/upload/v1/doc1_gst.pdf");
            return userRepository.save(u);
        });

        vendor2 = userRepository.findByUsername("doc_vendor_two").orElseGet(() -> {
            AppUser u = new AppUser();
            u.setUsername("doc_vendor_two");
            u.setEmail("doc2@vendor.test");
            u.setPassword("ValidPass123!@#");
            u.setUserDomain("VENDOR");
            u.setRoles(Set.of(roleVendor));
            u.setGstDocumentUrl("https://res.cloudinary.com/test/image/upload/v1/doc2_gst.pdf");
            return userRepository.save(u);
        });

        Client client1 = clientRepository.save(new Client());
        client1.setCompanyName("Client One");
        client1.setOwner(vendor1);
        client1 = clientRepository.save(client1);

        Client client2 = clientRepository.save(new Client());
        client2.setCompanyName("Client Two");
        client2.setOwner(vendor2);
        client2 = clientRepository.save(client2);

        invoice1 = new Invoice();
        invoice1.setInvoiceNumber("INV-DOC-1");
        invoice1.setInvoiceDate(LocalDate.now());
        invoice1.setClient(client1);
        invoice1.setFileUrl("https://res.cloudinary.com/test/image/upload/v1/invoice1.pdf");
        invoice1 = invoiceRepository.save(invoice1);

        invoice2 = new Invoice();
        invoice2.setInvoiceNumber("INV-DOC-2");
        invoice2.setInvoiceDate(LocalDate.now());
        invoice2.setClient(client2);
        invoice2.setFileUrl("https://res.cloudinary.com/test/image/upload/v1/invoice2.pdf");
        invoice2 = invoiceRepository.save(invoice2);

        ticket1 = new VendorTicket();
        ticket1.setTicketNo("TCK-DOC-1");
        ticket1.setOwner(vendor1);
        ticket1.setVendor(vendor1);
        ticket1.setInvoiceNo("INV-DOC-1");
        ticket1.setInvoice(invoice1);
        ticket1.setInvoiceFileUrl("https://res.cloudinary.com/test/image/upload/v1/tck1_inv.pdf");
        ticket1.setStatusRequest(TicketStatus.OPEN);
        ticket1.setCreatedAt(LocalDateTime.now());
        ticket1 = ticketRepository.save(ticket1);

        ticket2 = new VendorTicket();
        ticket2.setTicketNo("TCK-DOC-2");
        ticket2.setOwner(vendor2);
        ticket2.setVendor(vendor2);
        ticket2.setInvoiceNo("INV-DOC-2");
        ticket2.setInvoice(invoice2);
        ticket2.setInvoiceFileUrl("https://res.cloudinary.com/test/image/upload/v1/tck2_inv.pdf");
        ticket2.setStatusRequest(TicketStatus.OPEN);
        ticket2.setCreatedAt(LocalDateTime.now());
        ticket2 = ticketRepository.save(ticket2);
    }

    @AfterEach
    void tearDown() {
        ticketRepository.deleteAll();
        invoiceRepository.deleteAll();
        clientRepository.deleteAll();
        if (vendor1 != null && vendor1.getId() != null) {
            userRepository.deleteById(vendor1.getId());
        }
        if (vendor2 != null && vendor2.getId() != null) {
            userRepository.deleteById(vendor2.getId());
        }
    }

    @Test
    @DisplayName("Staff (ADMIN) can access any vendor profile document")
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    void staffCanAccessAnyVendorDocument() throws Exception {
        mockMvc.perform(get("/documents/vendor/" + vendor1.getId() + "/gst"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Staff (ACCOUNTS) can access any ticket document")
    @WithMockUser(username = "accounts_user", roles = {"ACCOUNTS"})
    void accountsCanAccessAnyTicketDocument() throws Exception {
        mockMvc.perform(get("/documents/ticket/" + ticket1.getId() + "/invoice"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Staff (SUPER_ADMIN) can access any invoice document")
    @WithMockUser(username = "super_admin_user", roles = {"SUPER_ADMIN"})
    void superAdminCanAccessAnyInvoiceDocument() throws Exception {
        mockMvc.perform(get("/documents/invoice/" + invoice1.getId()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Vendor can access their own profile document")
    @WithMockUser(username = "doc_vendor_one", roles = {"VENDOR"})
    void vendorCanAccessOwnProfileDocument() throws Exception {
        mockMvc.perform(get("/documents/vendor/" + vendor1.getId() + "/gst"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Vendor can access their own ticket document")
    @WithMockUser(username = "doc_vendor_one", roles = {"VENDOR"})
    void vendorCanAccessOwnTicketDocument() throws Exception {
        mockMvc.perform(get("/documents/ticket/" + ticket1.getId() + "/invoice"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Vendor can access their own invoice document")
    @WithMockUser(username = "doc_vendor_one", roles = {"VENDOR"})
    void vendorCanAccessOwnInvoiceDocument() throws Exception {
        mockMvc.perform(get("/documents/invoice/" + invoice1.getId()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Vendor is forbidden (403) from accessing another vendor's profile document")
    @WithMockUser(username = "doc_vendor_one", roles = {"VENDOR"})
    void vendorCannotAccessOtherVendorProfileDocument() throws Exception {
        mockMvc.perform(get("/documents/vendor/" + vendor2.getId() + "/gst"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Vendor is forbidden (403) from accessing another vendor's ticket document")
    @WithMockUser(username = "doc_vendor_one", roles = {"VENDOR"})
    void vendorCannotAccessOtherVendorTicketDocument() throws Exception {
        mockMvc.perform(get("/documents/ticket/" + ticket2.getId() + "/invoice"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Vendor is forbidden (403) from accessing another vendor's invoice document")
    @WithMockUser(username = "doc_vendor_one", roles = {"VENDOR"})
    void vendorCannotAccessOtherVendorInvoiceDocument() throws Exception {
        mockMvc.perform(get("/documents/invoice/" + invoice2.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Non-existent document ID returns 404")
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    void nonExistentDocumentReturns404() throws Exception {
        mockMvc.perform(get("/documents/invoice/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Anonymous access to documents is redirected to login")
    void anonymousCannotAccessDocuments() throws Exception {
        mockMvc.perform(get("/documents/invoice/" + invoice1.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/vendor/login")));
    }
}
