package com.billing.invoicehub;

import com.billing.invoicehub.entity.AppRole;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.TicketStatus;
import com.billing.invoicehub.entity.VendorTicket;
import com.billing.invoicehub.repository.AppRoleRepository;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.VendorTicketRepository;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"rate-limiting.enabled=false"})
class VendorReportExportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AppRoleRepository roleRepository;

    @Autowired
    private VendorTicketRepository ticketRepository;

    private AppUser vendor1;
    private AppUser vendor2;
    private AppUser vendor3;
    private final List<VendorTicket> seededTickets = new ArrayList<>();

    @BeforeEach
    void setupData() {
        cleanupData();

        AppRole vendorRole = roleRepository.findByName("ROLE_VENDOR")
                .orElseGet(() -> {
                    AppRole r = new AppRole();
                    r.setName("ROLE_VENDOR");
                    return roleRepository.save(r);
                });

        // Vendor 1: Verified, 2 documents, leading-zero GST
        vendor1 = new AppUser();
        vendor1.setUsername("test_v1_export");
        vendor1.setPassword("Secret123!");
        vendor1.setEmail("v1.export@test.com");
        vendor1.setUserDomain("VENDOR");
        vendor1.setCompanyName("Alpha Technologies PT");
        vendor1.setFullName("Budi Santoso");
        vendor1.setPhone("+6281234567890");
        vendor1.setGstNumber("010602977055000");
        vendor1.setAddress("Jl. Sudirman No. 10, Jakarta");
        vendor1.setVendorCode("VND-001");
        vendor1.setVerified(true);
        vendor1.setEnabled(true);
        vendor1.setRegistrationDate(LocalDateTime.of(2026, 3, 15, 10, 30));
        vendor1.setGstDocumentUrl("http://storage.test/v1/gst.pdf");
        vendor1.setCompanyDocumentUrl("http://storage.test/v1/comp.pdf");
        vendor1.setRoles(Set.of(vendorRole));
        vendor1 = userRepository.save(vendor1);

        // Vendor 2: Rejected, 1 document
        vendor2 = new AppUser();
        vendor2.setUsername("test_v2_export");
        vendor2.setPassword("Secret123!");
        vendor2.setEmail("v2.export@test.com");
        vendor2.setUserDomain("VENDOR");
        vendor2.setCompanyName("Beta Logistics CV");
        vendor2.setFullName("Dewi Lestari");
        vendor2.setPhone("081987654321");
        vendor2.setGstNumber("020504030201000");
        vendor2.setAddress("Jl. Thamrin No. 20, Jakarta Pusat");
        vendor2.setVendorCode("VND-002");
        vendor2.setVerified(false);
        vendor2.setRejectionReason("NPWP document is blurred");
        vendor2.setEnabled(true);
        vendor2.setRegistrationDate(LocalDateTime.of(2026, 4, 10, 14, 15));
        vendor2.setGstDocumentUrl("http://storage.test/v2/gst.pdf");
        vendor2.setRoles(Set.of(vendorRole));
        vendor2 = userRepository.save(vendor2);

        // Vendor 3: Pending, 3 documents
        vendor3 = new AppUser();
        vendor3.setUsername("test_v3_export");
        vendor3.setPassword("Secret123!");
        vendor3.setEmail("v3.export@test.com");
        vendor3.setUserDomain("VENDOR");
        vendor3.setCompanyName("Gamma Solutions PT");
        vendor3.setFullName("Ahmad Fauzi");
        vendor3.setPhone("085512345678");
        vendor3.setGstNumber("030605040302000");
        vendor3.setAddress("Jl. Gatot Subroto No. 30, Jakarta Selatan");
        vendor3.setVendorCode("VND-003");
        vendor3.setVerified(false);
        vendor3.setRejectionReason(null);
        vendor3.setEnabled(true);
        vendor3.setRegistrationDate(LocalDateTime.of(2026, 5, 20, 9, 0));
        vendor3.setGstDocumentUrl("http://storage.test/v3/gst.pdf");
        vendor3.setCompanyDocumentUrl("http://storage.test/v3/comp.pdf");
        vendor3.setSupportingDocumentUrl("http://storage.test/v3/sup.pdf");
        vendor3.setRoles(Set.of(vendorRole));
        vendor3 = userRepository.save(vendor3);

        // Seed 54 tickets matching exact numbers:
        // Total: 54
        // Pending (OPEN): 4
        // In Progress (IN_PROGRESS): 28
        // Paid (RESOLVED 12, PARTIALLY_PAID 4): 16
        // Rejected (REVISE): 0
        // Cancelled (CANCEL): 6
        // Sum = 4 + 28 + 16 + 0 + 6 = 54.

        int ticketCounter = 1;
        // 4 OPEN tickets -> vendor 1
        for (int i = 0; i < 4; i++) {
            seededTickets.add(createTicket(vendor1, TicketStatus.OPEN, new BigDecimal("1000000"), "TKT-" + (ticketCounter++)));
        }
        // 28 IN_PROGRESS tickets -> 14 on vendor 1, 14 on vendor 2
        for (int i = 0; i < 14; i++) {
            seededTickets.add(createTicket(vendor1, TicketStatus.IN_PROGRESS, new BigDecimal("2000000"), "TKT-" + (ticketCounter++)));
        }
        for (int i = 0; i < 14; i++) {
            seededTickets.add(createTicket(vendor2, TicketStatus.IN_PROGRESS, new BigDecimal("2500000"), "TKT-" + (ticketCounter++)));
        }
        // 12 RESOLVED tickets -> 6 on vendor 1, 6 on vendor 3
        for (int i = 0; i < 6; i++) {
            seededTickets.add(createTicket(vendor1, TicketStatus.RESOLVED, new BigDecimal("3000000"), "TKT-" + (ticketCounter++)));
        }
        for (int i = 0; i < 6; i++) {
            seededTickets.add(createTicket(vendor3, TicketStatus.RESOLVED, new BigDecimal("3500000"), "TKT-" + (ticketCounter++)));
        }
        // 4 PARTIALLY_PAID tickets -> vendor 2
        for (int i = 0; i < 4; i++) {
            seededTickets.add(createTicket(vendor2, TicketStatus.PARTIALLY_PAID, new BigDecimal("4000000"), "TKT-" + (ticketCounter++)));
        }
        // 6 CANCEL tickets -> 3 on vendor 2, 3 on vendor 3
        for (int i = 0; i < 3; i++) {
            seededTickets.add(createTicket(vendor2, TicketStatus.CANCEL, new BigDecimal("500000"), "TKT-" + (ticketCounter++)));
        }
        for (int i = 0; i < 3; i++) {
            seededTickets.add(createTicket(vendor3, TicketStatus.CANCEL, new BigDecimal("500000"), "TKT-" + (ticketCounter++)));
        }

        ticketRepository.saveAll(seededTickets);
    }

    private VendorTicket createTicket(AppUser vendor, TicketStatus status, BigDecimal amount, String ticketNo) {
        VendorTicket t = new VendorTicket();
        t.setVendor(vendor);
        t.setStatusRequest(status);
        t.setAmount(amount);
        t.setTotal(amount);
        t.setCurrency("IDR");
        t.setTicketNo(ticketNo);
        t.setInvoiceNo("INV-" + ticketNo);
        t.setPoNumber("PO-" + ticketNo);
        t.setCreatedAt(LocalDateTime.now().minusDays(2));
        return t;
    }

    @AfterEach
    void cleanupData() {
        ticketRepository.deleteAll();
        userRepository.findByUsername("test_v1_export").ifPresent(userRepository::delete);
        userRepository.findByUsername("test_v2_export").ifPresent(userRepository::delete);
        userRepository.findByUsername("test_v3_export").ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("Security: Non-admin users must receive 403 Forbidden on vendor report endpoints")
    void nonAdmin_shouldReceive403() throws Exception {
        // Vendor role visiting export -> 403
        mockMvc.perform(get("/admin/reports/vendors/export")
                        .with(user("vendor_user").roles("VENDOR")))
                .andExpect(status().isForbidden());

        // Vendor role visiting preview -> 403
        mockMvc.perform(get("/admin/reports/vendors/preview")
                        .with(user("vendor_user").roles("VENDOR")))
                .andExpect(status().isForbidden());

        // Anonymous user visiting export -> 302 to login or 401/403
        mockMvc.perform(get("/admin/reports/vendors/export"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Preview: GET /admin/reports/vendors/preview returns accurate counts")
    void previewEndpoint_shouldReturnAccurateCounts() throws Exception {
        mockMvc.perform(get("/admin/reports/vendors/preview")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.verified").value(1))
                .andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.pending").value(1));

        // Filter preview by status=VERIFIED
        mockMvc.perform(get("/admin/reports/vendors/preview")
                        .param("status", "VERIFIED")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.verified").value(1))
                .andExpect(jsonPath("$.rejected").value(0))
                .andExpect(jsonPath("$.pending").value(0));
    }

    @Test
    @DisplayName("Export: No filters generates complete Excel with 54 tickets matching dashboard (54 / 4 / 28 / 16 / 0 / 6)")
    void exportWithNoFilters_shouldGenerateAccurateWorkbook() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/reports/vendors/export")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().exists("Content-Disposition"))
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 0, "Excel byte array should not be empty");

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertEquals(2, wb.getNumberOfSheets(), "Workbook should contain 2 sheets");
            Sheet sheet1 = wb.getSheet("Vendors");
            assertNotNull(sheet1, "Sheet 1 'Vendors' must exist");
            Sheet sheet2 = wb.getSheet("Vendor Ticket Summary");
            assertNotNull(sheet2, "Sheet 2 'Vendor Ticket Summary' must exist");

            // --- Verify Sheet 1 (Vendors) ---
            // Row 0: Title block
            assertEquals("Vendor Report", sheet1.getRow(0).getCell(0).getStringCellValue());
            // Row 4: Header
            Row header1 = sheet1.getRow(4);
            assertEquals("No.", header1.getCell(0).getStringCellValue());
            assertEquals("Company Name", header1.getCell(1).getStringCellValue());
            assertEquals("Vendor Code", header1.getCell(2).getStringCellValue());
            assertEquals("Full Name", header1.getCell(3).getStringCellValue());
            assertEquals("Email", header1.getCell(4).getStringCellValue());
            assertEquals("Phone", header1.getCell(5).getStringCellValue());
            assertEquals("GST Number / NPWP", header1.getCell(6).getStringCellValue());
            assertEquals("Address", header1.getCell(7).getStringCellValue());
            assertEquals("Registration Date", header1.getCell(8).getStringCellValue());
            assertEquals("Verification Status", header1.getCell(9).getStringCellValue());
            assertEquals("Number of Documents Uploaded", header1.getCell(10).getStringCellValue());

            // 3 vendors: rows 5, 6, 7
            // Find Alpha Technologies (Vendor 1)
            Row v1Row = null;
            Row v2Row = null;
            Row v3Row = null;
            for (int r = 5; r <= 7; r++) {
                Row row = sheet1.getRow(r);
                String comp = row.getCell(1).getStringCellValue();
                if ("Alpha Technologies PT".equals(comp)) v1Row = row;
                else if ("Beta Logistics CV".equals(comp)) v2Row = row;
                else if ("Gamma Solutions PT".equals(comp)) v3Row = row;
            }

            assertNotNull(v1Row, "Vendor 1 row must exist");
            assertNotNull(v2Row, "Vendor 2 row must exist");
            assertNotNull(v3Row, "Vendor 3 row must exist");

            // Verify Vendor 1 details: leading-zero GST retained, phone + retained without leading apostrophe
            assertEquals("VND-001", v1Row.getCell(2).getStringCellValue());
            assertEquals("Budi Santoso", v1Row.getCell(3).getStringCellValue());
            assertEquals("v1.export@test.com", v1Row.getCell(4).getStringCellValue());
            assertEquals("+6281234567890", v1Row.getCell(5).getStringCellValue());
            assertEquals(CellType.STRING, v1Row.getCell(5).getCellType());
            assertEquals("010602977055000", v1Row.getCell(6).getStringCellValue());
            assertEquals(CellType.STRING, v1Row.getCell(6).getCellType(), "GST number must be stored as STRING cell");
            assertEquals("Jl. Sudirman No. 10, Jakarta", v1Row.getCell(7).getStringCellValue());
            assertEquals("15-03-2026 10:30", v1Row.getCell(8).getStringCellValue(), "Date format must be dd-MM-yyyy HH:mm");
            assertEquals("Verified", v1Row.getCell(9).getStringCellValue());
            assertEquals(2, (int) v1Row.getCell(10).getNumericCellValue());

            // Vendor 2 details: Rejected
            assertEquals("VND-002", v2Row.getCell(2).getStringCellValue());
            assertEquals("020504030201000", v2Row.getCell(6).getStringCellValue());
            assertEquals("10-04-2026 14:15", v2Row.getCell(8).getStringCellValue());
            assertEquals("Rejected", v2Row.getCell(9).getStringCellValue());
            assertEquals(1, (int) v2Row.getCell(10).getNumericCellValue());

            // Vendor 3 details: Pending
            assertEquals("VND-003", v3Row.getCell(2).getStringCellValue());
            assertEquals("Pending", v3Row.getCell(9).getStringCellValue());
            assertEquals(3, (int) v3Row.getCell(10).getNumericCellValue());

            // --- Verify Sheet 2 (Vendor Ticket Summary) ---
            assertEquals("Vendor Ticket Summary", sheet2.getRow(0).getCell(0).getStringCellValue());
            Row header2 = sheet2.getRow(4);
            assertEquals("No.", header2.getCell(0).getStringCellValue());
            assertEquals("Company Name", header2.getCell(1).getStringCellValue());
            assertEquals("Total Tickets", header2.getCell(2).getStringCellValue());
            assertEquals("Pending", header2.getCell(3).getStringCellValue());
            assertEquals("In Progress", header2.getCell(4).getStringCellValue());
            assertEquals("Paid", header2.getCell(5).getStringCellValue());
            assertEquals("Rejected", header2.getCell(6).getStringCellValue());
            assertEquals("Cancelled", header2.getCell(7).getStringCellValue());
            assertEquals("Total Invoice Amount", header2.getCell(8).getStringCellValue());

            // Check TOTAL row (row index 8 after rows 5, 6, 7)
            Row totalRow = sheet2.getRow(8);
            assertNotNull(totalRow, "TOTAL row must be present");
            assertEquals("TOTAL", totalRow.getCell(1).getStringCellValue());

            // Verify totals match dashboard: Total 54 / Pending 4 / In Progress 28 / Paid 16 / Rejected 0 / Cancelled 6
            int totalTickets = (int) totalRow.getCell(2).getNumericCellValue();
            int pendingTickets = (int) totalRow.getCell(3).getNumericCellValue();
            int inProgressTickets = (int) totalRow.getCell(4).getNumericCellValue();
            int paidTickets = (int) totalRow.getCell(5).getNumericCellValue();
            int rejectedTickets = (int) totalRow.getCell(6).getNumericCellValue();
            int cancelledTickets = (int) totalRow.getCell(7).getNumericCellValue();
            double totalAmount = totalRow.getCell(8).getNumericCellValue();

            assertEquals(54, totalTickets, "Sheet 2 Total Tickets must be 54");
            assertEquals(4, pendingTickets, "Sheet 2 Pending tickets must be 4");
            assertEquals(28, inProgressTickets, "Sheet 2 In Progress tickets must be 28");
            assertEquals(16, paidTickets, "Sheet 2 Paid tickets must be 16");
            assertEquals(0, rejectedTickets, "Sheet 2 Rejected tickets must be 0");
            assertEquals(6, cancelledTickets, "Sheet 2 Cancelled tickets must be 6");
            assertTrue(totalAmount > 0, "Total invoice amount must be positive");
            assertEquals(CellType.NUMERIC, totalRow.getCell(8).getCellType(), "Invoice Amount must be a NUMERIC cell");
        }
    }

    @Test
    @DisplayName("Export: Status filter (VERIFIED) exports only verified vendors")
    void exportWithStatusFilter_shouldFilterCorrectly() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/reports/vendors/export")
                        .param("status", "VERIFIED")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet1 = wb.getSheet("Vendors");
            // Only 1 vendor (row 5)
            assertNotNull(sheet1.getRow(5));
            assertEquals("Alpha Technologies PT", sheet1.getRow(5).getCell(1).getStringCellValue());
            assertEquals("Verified", sheet1.getRow(5).getCell(9).getStringCellValue());
            assertNull(sheet1.getRow(6), "Should only contain 1 vendor row");
        }
    }

    @Test
    @DisplayName("Export: Search filter matching NPWP exports matching vendor")
    void exportWithSearchFilter_shouldFilterCorrectly() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/reports/vendors/export")
                        .param("search", "010602977055000")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet1 = wb.getSheet("Vendors");
            assertNotNull(sheet1.getRow(5));
            assertEquals("Alpha Technologies PT", sheet1.getRow(5).getCell(1).getStringCellValue());
            assertNull(sheet1.getRow(6));
        }
    }

    @Test
    @DisplayName("Export: Date range filter exports vendors registered within dates")
    void exportWithDateRangeFilter_shouldFilterCorrectly() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/reports/vendors/export")
                        .param("startDate", "2026-04-01")
                        .param("endDate", "2026-04-30")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet1 = wb.getSheet("Vendors");
            // Only Vendor 2 was registered in April 2026
            assertNotNull(sheet1.getRow(5));
            assertEquals("Beta Logistics CV", sheet1.getRow(5).getCell(1).getStringCellValue());
            assertNull(sheet1.getRow(6));
        }
    }

    @Test
    @DisplayName("Export: When no vendors match filters, endpoint returns 404 with error message")
    void exportWithNoMatchingVendors_shouldReturn404() throws Exception {
        mockMvc.perform(get("/admin/reports/vendors/export")
                        .param("search", "NonExistentCompanyXYZ123")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().string("No vendors found for the selected filters"));
    }
}
