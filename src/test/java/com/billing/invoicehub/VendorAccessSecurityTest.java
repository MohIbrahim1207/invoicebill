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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limiting.enabled=false"
})
class VendorAccessSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AppRoleRepository roleRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private VendorTicketRepository ticketRepository;

    private AppUser vendorA;
    private AppUser vendorB;
    private Invoice invoiceA;
    private Invoice invoiceB;
    private VendorTicket ticketA;
    private VendorTicket ticketB;

    @BeforeEach
    void setUp() {
        AppRole roleVendor = roleRepository.findByName("ROLE_VENDOR")
                .orElseGet(() -> {
                    AppRole r = new AppRole();
                    r.setName("ROLE_VENDOR");
                    return roleRepository.save(r);
                });

        vendorA = userRepository.findByUsername("audit_vendor_a").orElseGet(() -> {
            AppUser u = new AppUser();
            u.setUsername("audit_vendor_a");
            u.setEmail("vendor_a@test.com");
            u.setPassword("ValidPass123!@#");
            u.setUserDomain("VENDOR");
            u.setRoles(Set.of(roleVendor));
            u.setGstDocumentUrl("https://res.cloudinary.com/test/image/upload/v1/vendorA_gst.pdf");
            return userRepository.save(u);
        });

        vendorB = userRepository.findByUsername("audit_vendor_b").orElseGet(() -> {
            AppUser u = new AppUser();
            u.setUsername("audit_vendor_b");
            u.setEmail("vendor_b@test.com");
            u.setPassword("ValidPass123!@#");
            u.setUserDomain("VENDOR");
            u.setRoles(Set.of(roleVendor));
            u.setGstDocumentUrl("https://res.cloudinary.com/test/image/upload/v1/vendorB_gst.pdf");
            return userRepository.save(u);
        });

        Client clientA = clientRepository.save(new Client());
        clientA.setCompanyName("Client of Vendor A");
        clientA.setOwner(vendorA);
        clientA = clientRepository.save(clientA);

        Client clientB = clientRepository.save(new Client());
        clientB.setCompanyName("Client of Vendor B");
        clientB.setOwner(vendorB);
        clientB = clientRepository.save(clientB);

        invoiceA = new Invoice();
        invoiceA.setInvoiceNumber("INV-A-" + System.currentTimeMillis());
        invoiceA.setInvoiceDate(LocalDate.now());
        invoiceA.setAmount(new BigDecimal("1000000"));
        invoiceA.setClient(clientA);
        invoiceA.setStatus("Pending");
        invoiceA.setFileUrl("https://res.cloudinary.com/test/image/upload/v1/invA.pdf");
        invoiceA = invoiceRepository.save(invoiceA);

        invoiceB = new Invoice();
        invoiceB.setInvoiceNumber("INV-B-" + System.currentTimeMillis());
        invoiceB.setInvoiceDate(LocalDate.now());
        invoiceB.setAmount(new BigDecimal("2000000"));
        invoiceB.setClient(clientB);
        invoiceB.setStatus("Pending");
        invoiceB.setFileUrl("https://res.cloudinary.com/test/image/upload/v1/invB.pdf");
        invoiceB = invoiceRepository.save(invoiceB);

        ticketA = new VendorTicket();
        ticketA.setTicketNo("TCK-A-" + System.currentTimeMillis());
        ticketA.setInvoiceNo(invoiceA.getInvoiceNumber());
        ticketA.setVendor(vendorA);
        ticketA.setOwner(vendorA);
        ticketA.setClient(clientA);
        ticketA.setInvoice(invoiceA);
        ticketA.setInvoiceFileUrl("https://res.cloudinary.com/test/image/upload/v1/ticketA_doc.pdf");
        ticketA.setStatusRequest(TicketStatus.OPEN);
        ticketA.setCreatedAt(LocalDateTime.now());
        ticketA = ticketRepository.save(ticketA);

        ticketB = new VendorTicket();
        ticketB.setTicketNo("TCK-B-" + System.currentTimeMillis());
        ticketB.setInvoiceNo(invoiceB.getInvoiceNumber());
        ticketB.setVendor(vendorB);
        ticketB.setOwner(vendorB);
        ticketB.setClient(clientB);
        ticketB.setInvoice(invoiceB);
        ticketB.setInvoiceFileUrl("https://res.cloudinary.com/test/image/upload/v1/ticketB_doc.pdf");
        ticketB.setStatusRequest(TicketStatus.OPEN);
        ticketB.setCreatedAt(LocalDateTime.now());
        ticketB = ticketRepository.save(ticketB);
    }

    @AfterEach
    void tearDown() {
        ticketRepository.deleteAll();
        invoiceRepository.deleteAll();
        clientRepository.deleteAll();
        if (vendorA != null && vendorA.getId() != null) {
            userRepository.deleteById(vendorA.getId());
        }
        if (vendorB != null && vendorB.getId() != null) {
            userRepository.deleteById(vendorB.getId());
        }
    }

    @Test
    @DisplayName("Vendor A can view their own invoice")
    @WithMockUser(username = "audit_vendor_a", roles = {"VENDOR"})
    void vendorACanViewOwnInvoice() throws Exception {
        mockMvc.perform(get("/invoice/" + invoiceA.getId()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Vendor A gets 403 Forbidden when trying to view Vendor B's invoice")
    @WithMockUser(username = "audit_vendor_a", roles = {"VENDOR"})
    void vendorAGets403ForVendorBInvoice() throws Exception {
        mockMvc.perform(get("/invoice/" + invoiceB.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Vendor A gets 403 Forbidden when trying to edit Vendor B's invoice")
    @WithMockUser(username = "audit_vendor_a", roles = {"VENDOR"})
    void vendorAGets403ForVendorBInvoiceEdit() throws Exception {
        mockMvc.perform(get("/invoice/" + invoiceB.getId() + "/edit"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Vendor A gets 403 Forbidden when trying to delete Vendor B's invoice")
    @WithMockUser(username = "audit_vendor_a", roles = {"VENDOR"})
    void vendorAGets403ForVendorBInvoiceDelete() throws Exception {
        mockMvc.perform(post("/deleteInvoice/" + invoiceB.getId()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Vendor A gets 404 Not Found for non-existent invoice")
    @WithMockUser(username = "audit_vendor_a", roles = {"VENDOR"})
    void nonExistentInvoiceReturns404() throws Exception {
        mockMvc.perform(get("/invoice/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Vendor A can view their own ticket history")
    @WithMockUser(username = "audit_vendor_a", roles = {"VENDOR"})
    void vendorACanViewOwnTicketHistory() throws Exception {
        mockMvc.perform(get("/vendor-tickets/" + ticketA.getId() + "/history"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Vendor A gets 403 Forbidden when trying to view Vendor B's ticket history")
    @WithMockUser(username = "audit_vendor_a", roles = {"VENDOR"})
    void vendorAGets403ForVendorBTicketHistory() throws Exception {
        mockMvc.perform(get("/vendor-tickets/" + ticketB.getId() + "/history"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Vendor A gets 403 Forbidden when trying to cancel Vendor B's ticket")
    @WithMockUser(username = "audit_vendor_a", roles = {"VENDOR"})
    void vendorAGets403ForVendorBTicketCancel() throws Exception {
        mockMvc.perform(post("/vendor-tickets/" + ticketB.getId() + "/cancel").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Vendor A gets 404 Not Found for non-existent ticket")
    @WithMockUser(username = "audit_vendor_a", roles = {"VENDOR"})
    void nonExistentTicketReturns404() throws Exception {
        mockMvc.perform(get("/vendor-tickets/999999/history"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Vendor A gets 403 Forbidden when accessing Vendor B's ticket document")
    @WithMockUser(username = "audit_vendor_a", roles = {"VENDOR"})
    void vendorAGets403ForVendorBTicketDocument() throws Exception {
        mockMvc.perform(get("/documents/ticket/" + ticketB.getId() + "/invoice"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Vendor A gets 403 Forbidden when accessing Vendor B's invoice document")
    @WithMockUser(username = "audit_vendor_a", roles = {"VENDOR"})
    void vendorAGets403ForVendorBInvoiceDocument() throws Exception {
        mockMvc.perform(get("/documents/invoice/" + invoiceB.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Vendor A gets 403 Forbidden when accessing Vendor B's vendor profile document")
    @WithMockUser(username = "audit_vendor_a", roles = {"VENDOR"})
    void vendorAGets403ForVendorBProfileDocument() throws Exception {
        mockMvc.perform(get("/documents/vendor/" + vendorB.getId() + "/gst"))
                .andExpect(status().isForbidden());
    }
}
