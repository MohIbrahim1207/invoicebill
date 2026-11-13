package com.billing.invoicehub;

import com.billing.invoicehub.entity.AppRole;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.SystemSetting;
import com.billing.invoicehub.repository.AppRoleRepository;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.SystemSettingRepository;
import com.billing.invoicehub.service.EmailService;
import com.billing.invoicehub.service.EmailSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.List;
import java.util.Locale;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.security.hibp.enabled=false",
        "app.base-url=https://invoicehub.cloud"
})
@Transactional
public class EmailNotificationAndSettingsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmailSettingService emailSettingService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private SystemSettingRepository systemSettingRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AppRoleRepository appRoleRepository;

    @Autowired
    private SpringTemplateEngine templateEngine;

    @BeforeEach
    public void setUp() {
        // Reset email CC and copy mode setting in database and memory
        systemSettingRepository.deleteById(EmailSettingService.KEY_EMAIL_NOTIFICATION_CC);
        systemSettingRepository.deleteById(EmailSettingService.KEY_EMAIL_NOTIFICATION_COPY_MODE);
        emailSettingService.refreshFromDatabase();

        // Ensure roles and super admin exist
        AppRole roleSuperAdmin = appRoleRepository.findByName("ROLE_SUPER_ADMIN").orElseGet(() -> {
            AppRole r = new AppRole();
            r.setName("ROLE_SUPER_ADMIN");
            return appRoleRepository.save(r);
        });

        if (appUserRepository.findByUsername("superadmin").isEmpty()) {
            AppUser sa = new AppUser();
            sa.setUsername("superadmin");
            sa.setEmail("superadmin@flow-force.com");
            sa.setPassword("SuperAdmin123!");
            sa.setUserDomain("STAFF");
            sa.setVerified(true);
            sa.getRoles().add(roleSuperAdmin);
            appUserRepository.save(sa);
        }
    }

    @Test
    @DisplayName("Email Settings: Default CC list is umapathi@flow-force.com, operations@flow-force.com and default mode is BCC")
    public void testDefaultCcSetting() {
        emailSettingService.refreshFromDatabase();
        String cc = emailSettingService.getNotificationCcSetting();
        assertNotNull(cc);
        assertTrue(cc.contains("umapathi@flow-force.com"));
        assertTrue(cc.contains("operations@flow-force.com"));

        String copyMode = emailSettingService.getNotificationCopyMode();
        assertEquals("BCC", copyMode);
        assertTrue(emailSettingService.isBccMode());
    }

    @Test
    @DisplayName("Email Settings: Copy mode persistence, switching between BCC and CC, and normalization")
    public void testCopyModePersistenceAndSwitching() {
        // Switch to CC
        emailSettingService.setNotificationCopyMode("CC", "superadmin");
        assertEquals("CC", emailSettingService.getNotificationCopyMode());
        assertFalse(emailSettingService.isBccMode());

        SystemSetting stored = systemSettingRepository.findById(EmailSettingService.KEY_EMAIL_NOTIFICATION_COPY_MODE).orElse(null);
        assertNotNull(stored);
        assertEquals("CC", stored.getValue());

        // Switch back to BCC
        emailSettingService.setNotificationCopyMode("BCC", "superadmin");
        assertEquals("BCC", emailSettingService.getNotificationCopyMode());
        assertTrue(emailSettingService.isBccMode());

        // Invalid or null mode defaults to BCC
        emailSettingService.setNotificationCopyMode("INVALID_MODE", "superadmin");
        assertEquals("BCC", emailSettingService.getNotificationCopyMode());
    }

    @Test
    @DisplayName("Email Settings: Recipient exclusion prevents CCing the primary recipient")
    public void testRecipientExclusion() {
        // Recipient is an external vendor -> both CC addresses included
        List<String> ccForVendor = emailSettingService.getNotificationCcRecipients("vendor@company.com");
        assertEquals(2, ccForVendor.size());
        assertTrue(ccForVendor.contains("umapathi@flow-force.com"));
        assertTrue(ccForVendor.contains("operations@flow-force.com"));

        // Recipient IS umapathi@flow-force.com -> umapathi omitted from CC
        List<String> ccForUmapathi = emailSettingService.getNotificationCcRecipients("umapathi@flow-force.com");
        assertEquals(1, ccForUmapathi.size());
        assertFalse(ccForUmapathi.contains("umapathi@flow-force.com"));
        assertTrue(ccForUmapathi.contains("operations@flow-force.com"));

        // Recipient IS operations@flow-force.com -> operations omitted from CC
        List<String> ccForOps = emailSettingService.getNotificationCcRecipients("operations@flow-force.com");
        assertEquals(1, ccForOps.size());
        assertTrue(ccForOps.contains("umapathi@flow-force.com"));
        assertFalse(ccForOps.contains("operations@flow-force.com"));
    }

    @Test
    @DisplayName("Email Settings: Custom CC configuration via service persists and audits")
    public void testCustomCcSettingPersistence() {
        String newCc = "lead@flow-force.com, finance@flow-force.com";
        emailSettingService.setNotificationCcSetting(newCc, "superadmin");

        assertEquals(newCc, emailSettingService.getNotificationCcSetting());
        SystemSetting stored = systemSettingRepository.findById(EmailSettingService.KEY_EMAIL_NOTIFICATION_CC).orElse(null);
        assertNotNull(stored);
        assertEquals(newCc, stored.getValue());

        List<String> recipients = emailSettingService.getNotificationCcRecipients("vendor@abc.com");
        assertEquals(2, recipients.size());
        assertTrue(recipients.contains("lead@flow-force.com"));
        assertTrue(recipients.contains("finance@flow-force.com"));

        // Cleanup
        emailSettingService.setNotificationCcSetting(EmailSettingService.DEFAULT_NOTIFICATION_CC, "superadmin");
    }

    @Test
    @DisplayName("EmailService: Base URL and Login URL configuration")
    public void testLoginUrl() {
        String loginUrl = emailService.getLoginUrl();
        assertEquals("https://invoicehub.cloud/login", loginUrl);
    }

    @Test
    @DisplayName("Email Templates: All notification emails render Login to InvoiceHub button pointing to loginUrl")
    public void testEmailTemplatesIncludeLoginButton() {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("loginUrl", emailService.getLoginUrl());
        context.setVariable("vendorName", "PT Maju Mundur");
        context.setVariable("vendorCode", "VEND-100");
        context.setVariable("ticketNo", "T-100");
        context.setVariable("invoiceNo", "INV-100");
        context.setVariable("invoiceNumber", "INV-100");
        context.setVariable("invoiceDate", "2026-11-13");
        context.setVariable("clientName", "Flow Force");
        context.setVariable("poNumber", "PO-100");
        context.setVariable("subtotal", "Rp 1.000.000");
        context.setVariable("tax", "Rp 110.000");
        context.setVariable("total", "Rp 1.110.000");
        context.setVariable("viewUrl", "https://invoicehub.cloud/invoices/100");
        context.setVariable("reason", "Incomplete document");
        context.setVariable("companyName", "PT Maju Mundur");
        context.setVariable("newStatus", "Approved");
        context.setVariable("amount", "Rp 1.110.000");
        context.setVariable("statusDate", "2026-11-13");

        String[] notificationTemplates = {
                "email/vendor-approved",
                "email/vendor-rejected",
                "email/registration-received",
                "email/ticket-submitted",
                "email/ticket-cancelled",
                "email/invoice-status-update",
                "email/vendor-ticket-submitted-admin"
        };

        for (String tpl : notificationTemplates) {
            String rendered = templateEngine.process(tpl, context);
            assertNotNull(rendered, "Template " + tpl + " must render");
            assertTrue(rendered.contains("https://invoicehub.cloud/login"),
                    "Template " + tpl + " must contain login URL 'https://invoicehub.cloud/login'");
            assertTrue(rendered.contains("Login to InvoiceHub") || rendered.contains("Masuk ke InvoiceHub"),
                    "Template " + tpl + " must contain 'Login to InvoiceHub' button text");
        }
    }

    @Test
    @WithMockUser(username = "vendor1", roles = {"VENDOR"})
    @DisplayName("Security: /admin/settings is forbidden to VENDOR")
    public void testSettingsForbiddenToVendor() throws Exception {
        mockMvc.perform(get("/admin/settings"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin1", roles = {"ADMIN"})
    @DisplayName("Security: /admin/settings is forbidden to ADMIN (SUPER_ADMIN only)")
    public void testSettingsForbiddenToAdmin() throws Exception {
        mockMvc.perform(get("/admin/settings"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "superadmin", roles = {"SUPER_ADMIN"})
    @DisplayName("Admin Settings Page: SUPER_ADMIN can view and edit email CC and copy mode settings")
    public void testSettingsPageSuperAdmin() throws Exception {
        mockMvc.perform(get("/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/admin-settings"))
                .andExpect(model().attributeExists("notificationCc"))
                .andExpect(model().attributeExists("notificationCopyMode"))
                .andExpect(model().attributeExists("loginUrl"))
                .andExpect(content().string(containsString("Email Notification Settings")))
                .andExpect(content().string(containsString("umapathi@flow-force.com")))
                .andExpect(content().string(containsString("BCC (Blind Carbon Copy)")));

        // Update settings via POST including copyMode
        mockMvc.perform(post("/admin/settings/email-cc")
                        .with(csrf())
                        .param("notificationCc", "audit@flow-force.com, ops@flow-force.com")
                        .param("copyMode", "CC"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings?saved=true"));

        assertEquals("audit@flow-force.com, ops@flow-force.com", emailSettingService.getNotificationCcSetting());
        assertEquals("CC", emailSettingService.getNotificationCopyMode());
    }

    @Test
    @WithMockUser(username = "vendor1", roles = {"VENDOR"})
    @DisplayName("Security: /dev/mail/send-test is forbidden to VENDOR")
    public void testDevMailForbiddenToVendor() throws Exception {
        mockMvc.perform(get("/dev/mail/send-test").param("to", "test@flow-force.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin1", roles = {"ADMIN"})
    @DisplayName("Security: /dev/mail/send-test is forbidden to ADMIN (SUPER_ADMIN only)")
    public void testDevMailForbiddenToAdmin() throws Exception {
        mockMvc.perform(get("/dev/mail/send-test").param("to", "test@flow-force.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security: /dev/mail/send-test is rejected when unauthenticated")
    public void testDevMailRejectedUnauthenticated() throws Exception {
        mockMvc.perform(get("/dev/mail/send-test").param("to", "test@flow-force.com"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "superadmin", roles = {"SUPER_ADMIN"})
    @DisplayName("AI Usage Page: Displays AI Quote Converter Analytics with em-dash for zero data")
    public void testAiUsagePageConverterMetrics() throws Exception {
        mockMvc.perform(get("/admin/ai-usage").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/admin-ai-usage"))
                .andExpect(model().attributeExists("aiStats"))
                .andExpect(content().string(containsString("AI Quote Converter Analytics")))
                .andExpect(content().string(containsString("Total Conversions")))
                .andExpect(content().string(containsString("—")));
    }
}

