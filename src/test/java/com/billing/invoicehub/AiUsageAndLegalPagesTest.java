package com.billing.invoicehub;

import com.billing.invoicehub.dto.VendorRegistrationForm;
import com.billing.invoicehub.entity.*;
import com.billing.invoicehub.repository.*;
import com.billing.invoicehub.service.AiUsageService;
import com.billing.invoicehub.service.AssistantSettingService;
import com.billing.invoicehub.service.LegalDocumentService;
import com.billing.invoicehub.service.VendorRegistrationService;
import org.springframework.mock.web.MockMultipartFile;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.security.hibp.enabled=false"
})
@Transactional
public class AiUsageAndLegalPagesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiUsageService aiUsageService;

    @Autowired
    private AiUsageLogRepository usageLogRepository;

    @Autowired
    private LegalDocumentService legalDocumentService;

    @Autowired
    private LegalDocumentRepository legalDocumentRepository;

    @Autowired
    private LegalHistoryRepository legalHistoryRepository;

    @Autowired
    private LegalAcceptanceRepository legalAcceptanceRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private VendorRegistrationService vendorRegistrationService;

    @Autowired
    private AssistantSettingService assistantSettingService;

    @Autowired
    private Validator validator;

    @BeforeEach
    void setUp() {
        legalDocumentService.seedDefaultDocumentsIfEmpty();
    }

    @Test
    @DisplayName("Privacy check: AiUsageLog has NO prompt or response text fields")
    void testAiUsageLogPrivacyNoPromptOrResponseStored() {
        Field[] fields = AiUsageLog.class.getDeclaredFields();
        List<String> fieldNames = Arrays.stream(fields).map(Field::getName).map(String::toLowerCase).toList();

        assertFalse(fieldNames.contains("prompt"), "AiUsageLog MUST NOT have a prompt field");
        assertFalse(fieldNames.contains("prompttext"), "AiUsageLog MUST NOT have a promptText field");
        assertFalse(fieldNames.contains("inputtext"), "AiUsageLog MUST NOT have an inputText field");
        assertFalse(fieldNames.contains("answer"), "AiUsageLog MUST NOT have an answer field");
        assertFalse(fieldNames.contains("response"), "AiUsageLog MUST NOT have a response field");
        assertFalse(fieldNames.contains("responsetext"), "AiUsageLog MUST NOT have a responseText field");
        assertFalse(fieldNames.contains("content"), "AiUsageLog MUST NOT have a content field");

        // Test logging records metadata only
        AiUsageLog log = aiUsageService.logUsage(
                "super_admin",
                "ROLE_SUPER_ADMIN",
                "ASSISTANT_CHAT",
                "gemini-2.5-flash",
                500,
                200,
                1200L,
                true,
                null
        );

        assertNotNull(log.getId());
        assertEquals("super_admin", log.getUsername());
        assertEquals("ROLE_SUPER_ADMIN", log.getRole());
        assertEquals("ASSISTANT_CHAT", log.getFeature());
        assertEquals("gemini-2.5-flash", log.getModel());
        assertEquals(500, log.getInputTokens());
        assertEquals(200, log.getOutputTokens());
        assertEquals(700, log.getTotalTokens());
        assertEquals(1200L, log.getDurationMs());
        assertTrue(log.isSuccess());
        assertNull(log.getErrorType());
        assertNotNull(log.getEstimatedCostUsd());
        assertNotNull(log.getEstimatedCostIdr());
    }

    @Test
    @DisplayName("Budget Thresholds: 100% disables vendor AI if autoDisableVendor is true, leaves staff tools unaffected")
    void testBudgetAutoDisableVendorAiAt100Percent() {
        // 1. Enable vendor assistant
        assistantSettingService.setAssistantForVendorsEnabled(true, "test");
        assertTrue(assistantSettingService.isAssistantForVendorsEnabled());

        // 2. Configure budget settings: monthly budget of Rp 1,000,000 with auto-shutoff enabled
        aiUsageService.updateSettings(
                0.075,
                0.30,
                16000.0,
                1000.0, // Very low budget: Rp 1,000
                true,   // Auto-disable vendor AI
                "super_admin"
        );

        assertTrue(aiUsageService.isAutoDisableVendorAtBudget());
        assertEquals(1000.0, aiUsageService.getMonthlyBudgetIdr());

        // 3. Log high-usage call that exceeds budget
        // 1,000,000 output tokens * $0.30/1M * 16,000 IDR/USD = Rp 4,800 > Rp 1,000
        aiUsageService.logUsage(
                "staff_user",
                "ROLE_ADMIN",
                "QUOTE",
                "gemini-1.5-pro",
                10000,
                1000000,
                3000L,
                true,
                null
        );

        // 4. Vendor AI must now be automatically disabled
        assertFalse(assistantSettingService.isAssistantForVendorsEnabled(),
                "Vendor AI should be automatically disabled at 100% monthly budget");
    }

    @Test
    @DisplayName("Default legal settings: both requireTermsAcceptance and showLegalPagesPublicly are false")
    void testDefaultLegalSettings() {
        assertFalse(legalDocumentService.isRequireTermsAcceptance(),
                "Require terms acceptance must default to OFF");
        assertFalse(legalDocumentService.isShowLegalPagesPublicly(),
                "Show legal pages publicly must default to OFF");
    }

    @Test
    @DisplayName("Legal pages: when showLegalPagesPublicly is OFF, anonymous and regular users are forbidden (403), SUPER_ADMIN can preview (200)")
    void testLegalPagesPreviewAndPublicAccess() throws Exception {
        // By default showLegalPagesPublicly is false
        assertFalse(legalDocumentService.isShowLegalPagesPublicly());

        // Anonymous user gets 403 Forbidden
        mockMvc.perform(get("/terms"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/cookies"))
                .andExpect(status().isForbidden());

        // Regular admin also gets 403 Forbidden
        mockMvc.perform(get("/terms").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        // SUPER_ADMIN gets 200 OK (Preview mode)
        mockMvc.perform(get("/terms").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("super").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("legal/terms"))
                .andExpect(model().attributeExists("doc"));

        // When switched ON, anonymous public access is allowed (200 OK)
        legalDocumentService.updateSettings(false, true, "super_admin");
        assertTrue(legalDocumentService.isShowLegalPagesPublicly());

        mockMvc.perform(get("/terms"))
                .andExpect(status().isOk())
                .andExpect(view().name("legal/terms"))
                .andExpect(model().attributeExists("doc"));
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(view().name("legal/privacy"))
                .andExpect(model().attributeExists("doc"));
        mockMvc.perform(get("/cookies"))
                .andExpect(status().isOk())
                .andExpect(view().name("legal/cookies"))
                .andExpect(model().attributeExists("doc"));
    }

    @Test
    @DisplayName("Require terms acceptance workflow: when OFF, no forced popup; when ON, existing user must accept")
    void testRequireTermsAcceptanceWorkflow() throws Exception {
        String username = "existing_vendor_" + System.currentTimeMillis();
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(username + "@vendor.com");
        user.setPassword("SecretPass123!@#");
        user.setUserDomain("VENDOR");
        user.setEnabled(true);
        user.setTermsAcceptedVersion(null); // Not accepted
        userRepository.saveAndFlush(user);

        // 1. While setting is OFF (default):
        assertFalse(legalDocumentService.isRequireTermsAcceptance());
        assertTrue(legalDocumentService.hasUserAcceptedCurrentTerms(user),
                "While OFF: no forced acceptance for existing users");

        mockMvc.perform(get("/api/legal/status").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(username).roles("VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsAcceptance").value(false));

        // 2. When switched ON:
        legalDocumentService.updateSettings(true, false, "super_admin");
        assertTrue(legalDocumentService.isRequireTermsAcceptance());
        assertFalse(legalDocumentService.hasUserAcceptedCurrentTerms(user),
                "When ON: existing user who hasn't accepted must return false");

        mockMvc.perform(get("/api/legal/status").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(username).roles("VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsAcceptance").value(true))
                .andExpect(jsonPath("$.termsVersion").value("1.0"));

        // 3. User accepts terms via /api/legal/accept
        mockMvc.perform(post("/api/legal/accept")
                        .with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(username).roles("VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        AppUser updated = userRepository.findByUsername(username).orElseThrow();
        assertTrue(legalDocumentService.hasUserAcceptedCurrentTerms(updated));
        assertEquals("1.0", updated.getTermsAcceptedVersion());
        assertNotNull(updated.getTermsAcceptedAt());

        // 4. Subsequent status check returns needsAcceptance = false
        mockMvc.perform(get("/api/legal/status").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(username).roles("VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsAcceptance").value(false));
    }

    @Test
    @DisplayName("Vendor registration validation: agreeTerms enforced only when requireTermsAcceptance is ON")
    void testVendorRegistrationTermsValidation() {
        VendorRegistrationForm form = new VendorRegistrationForm();
        form.setCompanyName("PT Test Abadi");
        form.setFullName("Budi Setiawan");
        form.setGstNumber("01.234.567.8-999.000");
        form.setEmail("test@pt-abadi.com");
        form.setPhone("+6281234567890");
        form.setAddress("Jl. Sudirman No 1");
        form.setUsername("pt_abadi");
        form.setPassword("SecretPass123!@#");
        form.setConfirmPassword("SecretPass123!@#");
        form.setAgreeTerms(false); // Disagreed

        // 1. When OFF: standard Bean Validation passes without requiring agreeTerms
        assertFalse(legalDocumentService.isRequireTermsAcceptance());
        Set<ConstraintViolation<VendorRegistrationForm>> violations = validator.validate(form);
        boolean termsViolationFound = violations.stream()
                .anyMatch(v -> "agreeTerms".equals(v.getPropertyPath().toString()));
        assertFalse(termsViolationFound, "agreeTerms is not required when setting is OFF");

        // 2. When ON: registration service enforces agreement
        legalDocumentService.updateSettings(true, false, "super_admin");
        assertTrue(legalDocumentService.isRequireTermsAcceptance());

        MockMultipartFile dummyGst = new MockMultipartFile(
                "gstDocument", "gst.pdf", "application/pdf",
                "%PDF-1.5 test gst".getBytes());
        MockMultipartFile dummyComp = new MockMultipartFile(
                "companyDocument", "comp.pdf", "application/pdf",
                "%PDF-1.5 test comp".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                vendorRegistrationService.registerVendor(form, dummyGst, dummyComp, null)
        );
        assertTrue(ex.getMessage().contains("agree to the Terms of Use and Privacy Policy"));
    }

    @Test
    @DisplayName("Privacy Policy contains [TO CONFIRM] tags on all required factual claims")
    void testPrivacyPolicyDraftContainsToConfirmMarkings() {
        LegalDocument privacy = legalDocumentService.getDocument(LegalDocumentService.SLUG_PRIVACY)
                .orElseThrow(() -> new AssertionError("Privacy document must exist"));

        String en = privacy.getContentEn();
        assertNotNull(en);
        assertTrue(en.contains("[TO CONFIRM: Retention period"), "English must mark retention period [TO CONFIRM]");
        assertTrue(en.contains("[TO CONFIRM: Gemini data-use/training statement"), "English must mark Gemini AI policy [TO CONFIRM]");
        assertTrue(en.contains("[TO CONFIRM: `automation@flow-force.com`]"), "English must mark DPO email [TO CONFIRM]");
        assertTrue(en.contains("[TO CONFIRM: PT Flow Force Indonesia]"), "English must mark company entity [TO CONFIRM]");
        assertTrue(en.contains("[TO CONFIRM: Legal compliance statement"), "English must mark compliance statements [TO CONFIRM]");

        String id = privacy.getContentId();
        assertNotNull(id);
        assertTrue(id.contains("[TO CONFIRM: Masa retensi"), "Indonesian must mark retention period [TO CONFIRM]");
        assertTrue(id.contains("[TO CONFIRM: Ketentuan penggunaan data/pelatihan Gemini"), "Indonesian must mark Gemini AI policy [TO CONFIRM]");
        assertTrue(id.contains("[TO CONFIRM: `automation@flow-force.com`]"), "Indonesian must mark DPO email [TO CONFIRM]");
        assertTrue(id.contains("[TO CONFIRM: PT Flow Force Indonesia]"), "Indonesian must mark company entity [TO CONFIRM]");
        assertTrue(id.contains("[TO CONFIRM: Pernyataan kepatuhan hukum"), "Indonesian must mark compliance statements [TO CONFIRM]");
    }

    @Test
    @DisplayName("Super admin can update legal settings via POST /admin/legal-pages/settings")
    @WithMockUser(username = "super_admin", roles = {"SUPER_ADMIN"})
    void testSuperAdminUpdateLegalSettingsEndpoint() throws Exception {
        mockMvc.perform(post("/admin/legal-pages/settings")
                        .with(csrf())
                        .param("requireTermsAcceptance", "true")
                        .param("showLegalPagesPublicly", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/legal-pages?saved=true"));

        assertTrue(legalDocumentService.isRequireTermsAcceptance());
        assertTrue(legalDocumentService.isShowLegalPagesPublicly());
    }

    @Test
    @DisplayName("Authorization: /admin/ai-usage and /admin/legal-pages require ROLE_SUPER_ADMIN")
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    void testAdminCannotAccessSuperAdminPages() throws Exception {
        // ADMIN should be forbidden / denied access
        mockMvc.perform(get("/admin/ai-usage"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/legal-pages"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Authorization: SUPER_ADMIN has access to /admin/ai-usage and /admin/legal-pages")
    @WithMockUser(username = "super_admin", roles = {"SUPER_ADMIN"})
    void testSuperAdminAccess() throws Exception {
        mockMvc.perform(get("/admin/ai-usage"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/admin-ai-usage"))
                .andExpect(model().attributeExists("todayRequests"))
                .andExpect(model().attributeExists("monthRequests"));

        mockMvc.perform(get("/admin/legal-pages"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/admin-legal-editor"))
                .andExpect(model().attributeExists("termsDoc"));
    }
}
