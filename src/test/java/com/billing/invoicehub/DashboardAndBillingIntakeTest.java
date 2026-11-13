package com.billing.invoicehub;

import com.billing.invoicehub.dto.DashboardStatsDto;
import com.billing.invoicehub.entity.*;
import com.billing.invoicehub.repository.*;
import com.billing.invoicehub.service.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.security.hibp.enabled=false"
})
@Transactional
public class DashboardAndBillingIntakeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private VendorTicketRepository vendorTicketRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private AppRoleRepository appRoleRepository;

    @org.junit.jupiter.api.BeforeEach
    public void setUpAdminUser() {
        if (appUserRepository.findByUsername("admin").isEmpty()) {
            AppRole roleAdmin = appRoleRepository.findByName("ROLE_ADMIN").orElseGet(() -> {
                AppRole r = new AppRole();
                r.setName("ROLE_ADMIN");
                return appRoleRepository.save(r);
            });
            AppUser admin = new AppUser();
            admin.setUsername("admin");
            admin.setEmail("admin@invoicehub.com");
            admin.setPassword("Admin123!");
            admin.setUserDomain("STAFF");
            admin.setVerified(true);
            admin.getRoles().add(roleAdmin);
            appUserRepository.save(admin);
        }
    }

    @Test
    @DisplayName("Dashboard Service: Needs Attention, Status Amounts, Top Vendors, Recent Activity")
    public void testDashboardStatsOverviewExtensions() {
        AppRole roleVendor = appRoleRepository.findByName("ROLE_VENDOR").orElseGet(() -> {
            AppRole r = new AppRole();
            r.setName("ROLE_VENDOR");
            return appRoleRepository.save(r);
        });

        // Create an unverified vendor
        AppUser vendor = new AppUser();
        vendor.setUsername("test_vendor_overview_" + System.currentTimeMillis());
        vendor.setEmail("vendor_ov@" + System.currentTimeMillis() + ".com");
        vendor.setPassword("Secret123!");
        vendor.setCompanyName("PT Overview Tester");
        vendor.setVendorCode("VEND-OV");
        vendor.setUserDomain("VENDOR");
        vendor.setVerified(false);
        vendor.getRoles().add(roleVendor);
        vendor = appUserRepository.save(vendor);

        // Create client
        Client client = new Client();
        client.setCompanyName("Client Overview PT");
        client.setOwner(vendor);
        client = clientRepository.save(client);

        // 1. Overdue pending ticket (> 7 days)
        VendorTicket overdueTicket = new VendorTicket();
        overdueTicket.setTicketNo("T-OV-001");
        overdueTicket.setInvoiceNo("INV-OV-001");
        overdueTicket.setStatusRequest(TicketStatus.OPEN);
        overdueTicket.setCreatedAt(LocalDateTime.now().minusDays(10));
        overdueTicket.setVendor(vendor);
        overdueTicket.setOwner(vendor);
        overdueTicket.setClient(client);
        overdueTicket.setTotal(new BigDecimal("2500000"));
        vendorTicketRepository.save(overdueTicket);

        // 2. Unsynced ticket
        VendorTicket unsyncedTicket = new VendorTicket();
        unsyncedTicket.setTicketNo("T-OV-002");
        unsyncedTicket.setInvoiceNo("INV-OV-002");
        unsyncedTicket.setStatusRequest(TicketStatus.IN_PROGRESS);
        unsyncedTicket.setCreatedAt(LocalDateTime.now());
        unsyncedTicket.setVendor(vendor);
        unsyncedTicket.setOwner(vendor);
        unsyncedTicket.setClient(client);
        unsyncedTicket.setInvoice(null);
        unsyncedTicket.setTotal(new BigDecimal("1500000"));
        vendorTicketRepository.save(unsyncedTicket);

        // 3. Revised ticket waiting for vendor
        VendorTicket revisedTicket = new VendorTicket();
        revisedTicket.setTicketNo("T-OV-003");
        revisedTicket.setInvoiceNo("INV-OV-003");
        revisedTicket.setStatusRequest(TicketStatus.REVISE);
        revisedTicket.setCreatedAt(LocalDateTime.now());
        revisedTicket.setVendor(vendor);
        revisedTicket.setOwner(vendor);
        revisedTicket.setClient(client);
        revisedTicket.setTotal(new BigDecimal("3000000"));
        vendorTicketRepository.save(revisedTicket);

        // 4. Create an invoice for this month
        Invoice inv = new Invoice();
        inv.setInvoiceNumber("INV-OV-004");
        inv.setInvoiceDate(LocalDate.now());
        inv.setAmount(new BigDecimal("5000000"));
        inv.setStatus("Approved");
        inv.setClient(client);
        invoiceRepository.save(inv);

        // 5. Create an audit log
        AuditLog log = new AuditLog();
        log.setUsername("admin");
        log.setRole("ADMIN");
        log.setAction("Ticket Creation");
        log.setEntityType("VendorTicket");
        log.setEntityId(overdueTicket.getId());
        log.setCreatedAt(LocalDateTime.now());
        log.setNewValue("Created ticket T-OV-001");
        auditLogRepository.save(log);

        // Call service
        DashboardStatsDto stats = dashboardService.getDashboardStats();

        // Verify Needs Attention
        assertNotNull(stats.getNeedsAttention());
        assertTrue(stats.getNeedsAttention().getPendingOverdueCount() >= 1, "Pending overdue count must be >= 1");
        assertTrue(stats.getNeedsAttention().getUnsyncedCount() >= 1, "Unsynced count must be >= 1");
        assertTrue(stats.getNeedsAttention().getRevisedCount() >= 1, "Revised count must be >= 1");
        assertTrue(stats.getNeedsAttention().isHasItems());

        // Verify Status Amounts
        assertNotNull(stats.getStatusAmounts());
        assertFalse(stats.getStatusAmounts().isEmpty());
        boolean hasApprovedAmount = stats.getStatusAmounts().stream()
                .anyMatch(sa -> "Approved".equalsIgnoreCase(sa.getStatus()) && sa.getAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(hasApprovedAmount, "Approved status amount must be present");

        // Verify Top Vendors
        assertNotNull(stats.getTopVendors());
        assertFalse(stats.getTopVendors().isEmpty());
        boolean foundVendor = stats.getTopVendors().stream()
                .anyMatch(tv -> tv.getVendorName().contains("Overview Tester"));
        assertTrue(foundVendor, "Vendor must be in top vendors list");

        // Verify Recent Activities
        assertNotNull(stats.getRecentActivities());
        assertFalse(stats.getRecentActivities().isEmpty());
        boolean foundActivity = stats.getRecentActivities().stream()
                .anyMatch(a -> a.getEntityId() != null && a.getEntityId().equals(overdueTicket.getId()));
        assertTrue(foundActivity, "Recent activity must include the new audit log");
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Dashboard: KPI cards are clickable and overview panels are rendered")
    public void testDashboardMvcPage() throws Exception {
        mockMvc.perform(get("/admin/dashboard").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("dashboardStats"))
                // Clickable KPI card links
                .andExpect(content().string(containsString("/invoice?status=ALL")))
                .andExpect(content().string(containsString("/invoice?status=Pending")))
                .andExpect(content().string(containsString("/invoice?status=Approved")))
                // Needs Attention panel rendered
                .andExpect(content().string(containsString("Needs Attention")))
                .andExpect(content().string(containsString("/admin/tickets?status=OPEN")))
                .andExpect(content().string(containsString("/admin/tickets?unsynced=true")))
                .andExpect(content().string(containsString("/admin/tickets?status=REVISE")))
                // Amount by Status
                .andExpect(content().string(containsString("Amount by Status")))
                // Top 5 Vendors
                .andExpect(content().string(containsString("Top 5 Vendors This Month")))
                // 1.7.3: Recent activity table removed, small button to Audit Logs added
                .andExpect(content().string(containsString("/admin/audit-logs")))
                .andExpect(content().string(containsString("View activity log")))
                .andExpect(content().string(not(containsString("admin.audit.col.time"))))
                // 1.7.3: AI Quote Converter Analytics removed from dashboard
                .andExpect(content().string(not(containsString("AI Quote Converter Analytics"))))
                .andExpect(content().string(not(containsString("Total Conversions"))));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Billing Intake (/invoice): Work list tabs, no big summary cards")
    public void testBillingIntakeWorkListPage() throws Exception {
        mockMvc.perform(get("/invoice").locale(java.util.Locale.ENGLISH))
                .andExpect(status().isOk())
                .andExpect(view().name("invoice"))
                .andExpect(model().attributeExists("invoices"))
                .andExpect(model().attributeExists("statusCounts"))
                .andExpect(model().attributeExists("selectedStatus"))
                // Status tabs rendered
                .andExpect(content().string(containsString("statusTabsBar")))
                .andExpect(content().string(containsString("status-tab-btn")))
                .andExpect(content().string(containsString("status-tab-pending")))
                .andExpect(content().string(containsString("status-tab-approved")))
                // Big summary cards removed
                .andExpect(content().string(not(containsString("id=\"overviewSection\""))))
                // Collapsible charts removed from Billing Intake
                .andExpect(content().string(not(containsString("id=\"invoiceChart\""))))
                .andExpect(content().string(not(containsString("id=\"billingReportChart\""))));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Billing Intake (/invoice?status=Pending): Selected status retained")
    public void testBillingIntakeWithStatusQuery() throws Exception {
        mockMvc.perform(get("/invoice").param("status", "Pending"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedStatus", "Pending"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Admin Tickets: /admin/tickets?unsynced=true filters to unsynced tickets only")
    public void testAdminTicketsUnsyncedFilter() throws Exception {
        // Create an unsynced ticket
        VendorTicket t = new VendorTicket();
        t.setTicketNo("T-UNSYNC-999");
        t.setInvoiceNo("INV-UNSYNC-999");
        t.setStatusRequest(TicketStatus.OPEN);
        t.setInvoice(null);
        vendorTicketRepository.save(t);

        mockMvc.perform(get("/admin/tickets").param("unsynced", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-tickets"))
                .andExpect(model().attributeExists("tickets"))
                .andExpect(result -> {
                    @SuppressWarnings("unchecked")
                    List<VendorTicket> tickets = (List<VendorTicket>) result.getModelAndView().getModel().get("tickets");
                    assertNotNull(tickets);
                    assertTrue(tickets.stream().allMatch(x -> x.getInvoice() == null), "All returned tickets must be unsynced");
                });
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Reports page (/admin/reports): Date-range and export only, no dashboard stat cards")
    public void testReportsPageNoDashboardCards() throws Exception {
        mockMvc.perform(get("/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-reports"))
                // Filter form present
                .andExpect(content().string(containsString("id=\"filterForm\"")))
                .andExpect(content().string(containsString("startDate")))
                .andExpect(content().string(containsString("endDate")))
                // Export buttons present
                .andExpect(content().string(containsString("js-export-btn")))
                // Old duplicate stat card block removed
                .andExpect(content().string(not(containsString("Ticket Statistics Summary"))));
    }
}

