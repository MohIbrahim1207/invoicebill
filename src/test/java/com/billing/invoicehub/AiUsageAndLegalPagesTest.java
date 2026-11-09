package com.billing.invoicehub;

import com.billing.invoicehub.dto.VendorRegistrationForm;
import com.billing.invoicehub.entity.*;
import com.billing.invoicehub.repository.*;
import com.billing.invoicehub.service.AiUsageService;
import com.billing.invoicehub.service.AssistantSettingService;
import com.billing.invoicehub.service.LegalDocumentService;
import com.billing.invoicehub.service.VendorRegistrationService;
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
    @DisplayName("Legal pages (/terms, /privacy, /cookies) are publicly accessible without authentication")
    void testLegalPagesPublicAccess() throws Exception {
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
    @DisplayName("Vendor registration validation: agreeTerms=false fails, agreeTerms=true succeeds")
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

        Set<ConstraintViolation<VendorRegistrationForm>> violations = validator.validate(form);
        boolean termsViolationFound = violations.stream()
                .anyMatch(v -> "agreeTerms".equals(v.getPropertyPath().toString()));
        assertTrue(termsViolationFound, "agreeTerms=false must fail validation");

        // When agreed
        form.setAgreeTerms(true);
        violations = validator.validate(form);
        termsViolationFound = violations.stream()
                .anyMatch(v -> "agreeTerms".equals(v.getPropertyPath().toString()));
        assertFalse(termsViolationFound, "agreeTerms=true must pass validation");
    }

    @Test
    @DisplayName("Existing logged-in user can accept terms via /api/legal/accept")
    void testExistingUserTermsAcceptance() {
        String username = "existing_vendor_" + System.currentTimeMillis();
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(username + "@vendor.com");
        user.setPassword("SecretPass123!@#");
        user.setUserDomain("VENDOR");
        user.setEnabled(true);
        user.setTermsAcceptedVersion(null); // Not accepted
        userRepository.saveAndFlush(user);

        assertFalse(legalDocumentService.hasUserAcceptedCurrentTerms(user));

        // User accepts
        legalDocumentService.recordAcceptance(user, "192.168.1.100");

        AppUser updated = userRepository.findByUsername(username).orElseThrow();
        assertTrue(legalDocumentService.hasUserAcceptedCurrentTerms(updated));
        assertEquals("1.0", updated.getTermsAcceptedVersion());
        assertNotNull(updated.getTermsAcceptedAt());

        List<LegalAcceptance> acceptances = legalAcceptanceRepository.findAllByUsernameOrderByAcceptedAtDesc(username);
        assertFalse(acceptances.isEmpty());
        assertEquals("192.168.1.100", acceptances.get(0).getIpAddress());
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
