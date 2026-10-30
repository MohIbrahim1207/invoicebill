package com.billing.invoicehub;

import com.billing.invoicehub.entity.AppRole;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AppRoleRepository;
import com.billing.invoicehub.repository.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests verifying domain-scoped separation (VENDOR vs INTERNAL).
 */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = {"rate-limiting.enabled=false", "app.security.hibp.enabled=false"})
class PortalSeparationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AppRoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String ADMIN_USERNAME = "3p_admin_portal";
    private static final String VENDOR_USERNAME = "3p_vendor_portal";
    private static final String ACCOUNTS_USERNAME = "3p_accounts_portal";
    private static final String PLAIN_PASSWORD  = "TestPassword123!";

    private static final String DUAL_EMAIL = "john.dual@domain.test";

    @BeforeEach
    void seedUsers() {
        SecurityContextHolder.clearContext();
        AppRole adminRole = getOrCreateRole("ROLE_ADMIN");
        AppRole vendorRole = getOrCreateRole("ROLE_VENDOR");
        AppRole accountsRole = getOrCreateRole("ROLE_ACCOUNTS");

        createUserIfAbsent(ADMIN_USERNAME, "3p_admin@test.local", adminRole, "INTERNAL");
        createUserIfAbsent(VENDOR_USERNAME, "3p_vendor@test.local", vendorRole, "VENDOR");
        createUserIfAbsent(ACCOUNTS_USERNAME, "3p_accounts@test.local", accountsRole, "INTERNAL");

        SecurityContextHolder.clearContext();
    }

    private AppRole getOrCreateRole(String roleName) {
        return roleRepository.findFirstByName(roleName).orElseGet(() -> {
            AppRole r = new AppRole();
            r.setName(roleName);
            return roleRepository.save(r);
        });
    }

    private void createUserIfAbsent(String username, String email, AppRole role, String domain) {
        if (userRepository.findFirstByUsernameAndUserDomain(username, domain).isEmpty()) {
            AppUser u = new AppUser();
            u.setUserDomain(domain);
            u.setUsername(username);
            u.setPassword(passwordEncoder.encode(PLAIN_PASSWORD));
            u.setEmail(email);
            u.setEnabled(true);
            u.setVerified(true);
            u.setRegistrationDate(LocalDateTime.now());
            u.setRoles(Set.of(role));
            userRepository.save(u);
        }
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        userRepository.findFirstByUsernameAndUserDomain(ADMIN_USERNAME, "INTERNAL").ifPresent(userRepository::delete);
        userRepository.findFirstByUsernameAndUserDomain(VENDOR_USERNAME, "VENDOR").ifPresent(userRepository::delete);
        userRepository.findFirstByUsernameAndUserDomain(ACCOUNTS_USERNAME, "INTERNAL").ifPresent(userRepository::delete);

        userRepository.findFirstByEmailIgnoreCaseAndUserDomain(DUAL_EMAIL, "VENDOR").ifPresent(userRepository::delete);
        userRepository.findFirstByEmailIgnoreCaseAndUserDomain(DUAL_EMAIL, "INTERNAL").ifPresent(userRepository::delete);
    }

    private static MockHttpSession cleanSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, null);
        return session;
    }

    // ── DOMAIN SEPARATION TESTS (VENDOR vs INTERNAL) ─────────────────────────

    @Test
    @DisplayName("Domain Separation: Same email address allowed as Vendor AND Admin without interference")
    void sameEmailCrossDomain_shouldBeAllowedAndIsolated() throws Exception {
        AppRole vendorRole = getOrCreateRole("ROLE_VENDOR");
        AppRole adminRole = getOrCreateRole("ROLE_ADMIN");

        // 1. Create Vendor with dual email
        createUserIfAbsent("vendor_john", DUAL_EMAIL, vendorRole, "VENDOR");
        // 2. Create Admin with same dual email
        createUserIfAbsent("admin_john", DUAL_EMAIL, adminRole, "INTERNAL");

        // Both accounts exist in DB cleanly
        assertTrue(userRepository.existsByEmailIgnoreCaseAndUserDomain(DUAL_EMAIL, "VENDOR"));
        assertTrue(userRepository.existsByEmailIgnoreCaseAndUserDomain(DUAL_EMAIL, "INTERNAL"));

        // 3. Authenticate Vendor portal using dual email -> succeeds to /invoice
        mockMvc.perform(post("/vendor/authenticate")
                        .session(cleanSession())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", DUAL_EMAIL)
                        .param("password", PLAIN_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoice"));

        // 4. Authenticate Admin portal using dual email -> succeeds to /admin/dashboard
        mockMvc.perform(post("/admin/authenticate")
                        .session(cleanSession())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", DUAL_EMAIL)
                        .param("password", PLAIN_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));
    }

    // ── PORTAL 1: VENDOR PORTAL TESTS (/vendor/login, /vendor/authenticate) ──

    @Test
    @DisplayName("Portal 1: Vendor via Vendor Portal -> Success, redirect to /invoice")
    void vendorViaVendorPortal_shouldSucceed() throws Exception {
        mockMvc.perform(post("/vendor/authenticate")
                        .session(cleanSession())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", VENDOR_USERNAME)
                        .param("password", PLAIN_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoice"));
    }

    @Test
    @DisplayName("Portal 1: Non-Vendor (Admin) via Vendor Portal -> Denied, redirect with invalid credentials")
    void adminViaVendorPortal_shouldBeRejected() throws Exception {
        mockMvc.perform(post("/vendor/authenticate")
                        .session(cleanSession())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", ADMIN_USERNAME)
                        .param("password", PLAIN_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vendor/login?error=invalid"));
    }

    // ── PORTAL 2: ADMIN PORTAL TESTS (/admin/login, /admin/authenticate) ──

    @Test
    @DisplayName("Portal 2: Admin via Admin Portal -> Success, redirect to /admin/dashboard")
    void adminViaAdminPortal_shouldSucceed() throws Exception {
        mockMvc.perform(post("/admin/authenticate")
                        .session(cleanSession())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", ADMIN_USERNAME)
                        .param("password", PLAIN_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    @DisplayName("Portal 2: Accounts via Admin Portal -> Success, redirect to /admin/dashboard")
    void accountsViaAdminPortal_shouldSucceed() throws Exception {
        mockMvc.perform(post("/admin/authenticate")
                        .session(cleanSession())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", ACCOUNTS_USERNAME)
                        .param("password", PLAIN_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    @DisplayName("Portal 2: Vendor via Admin Portal -> Denied, redirect with invalid credentials")
    void vendorViaAdminPortal_shouldBeRejected() throws Exception {
        mockMvc.perform(post("/admin/authenticate")
                        .session(cleanSession())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", VENDOR_USERNAME)
                        .param("password", PLAIN_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?error=invalid"));
    }

    @Autowired
    private com.billing.invoicehub.service.EmployeeUserService employeeUserService;

    @Test
    @DisplayName("Validation: Cross-Domain Duplicate Email Creation on /admin/users/create is Hard-Blocked")
    void createDuplicateCrossDomainUser_shouldBeBlocked() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            employeeUserService.createUser("Duplicate Tester", "3p_vendor@test.local", "TestPassword123!", "ROLE_ACCOUNTS", null);
        });
        assertEquals("This email is already registered as a Vendor account. Each email may only be associated with one portal domain.", ex.getMessage());
    }
}
