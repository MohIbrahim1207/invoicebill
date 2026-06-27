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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests verifying strict portal-role separation.
 *
 * <p>Each test creates a genuinely clean {@link MockHttpSession} — explicitly setting the
 * {@code SPRING_SECURITY_CONTEXT} attribute to {@code null} so Spring Security's
 * {@link HttpSessionSecurityContextRepository} cannot find a pre-existing authenticated
 * context and thus is forced to run the authentication filter chain.</p>
 *
 * <ol>
 *   <li>Admin via Vendor portal  → rejected, redirected to /login?error=admin_portal</li>
 *   <li>Vendor via Admin portal  → rejected, redirected to /admin/login?error=vendor_portal</li>
 *   <li>Admin via Admin portal   → success, redirected to /admin/dashboard</li>
 *   <li>Vendor via Vendor portal → success, redirected to /invoice</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
class PortalSeparationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AppRoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String ADMIN_USERNAME = "ptst_admin_portal";
    private static final String VENDOR_USERNAME = "ptst_vendor_portal";
    private static final String PLAIN_PASSWORD  = "TestPassword123!";

    @BeforeEach
    void seedUsers() {
        SecurityContextHolder.clearContext();
        // ── Admin role ──────────────────────────────────────────────────────
        AppRole adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> {
                    AppRole r = new AppRole();
                    r.setName("ROLE_ADMIN");
                    return roleRepository.save(r);
                });

        // ── Vendor role ─────────────────────────────────────────────────────
        AppRole vendorRole = roleRepository.findByName("ROLE_VENDOR")
                .orElseGet(() -> {
                    AppRole r = new AppRole();
                    r.setName("ROLE_VENDOR");
                    return roleRepository.save(r);
                });

        // ── Admin user ───────────────────────────────────────────────────────
        if (userRepository.findByUsername(ADMIN_USERNAME).isEmpty()) {
            AppUser admin = new AppUser();
            admin.setUsername(ADMIN_USERNAME);
            admin.setPassword(passwordEncoder.encode(PLAIN_PASSWORD));
            admin.setEmail("ptst_admin@test.local");
            admin.setEnabled(true);
            admin.setVerified(true);
            admin.setRegistrationDate(LocalDateTime.now());
            admin.setRoles(Set.of(adminRole));
            userRepository.save(admin);
        }

        // ── Vendor user ──────────────────────────────────────────────────────
        if (userRepository.findByUsername(VENDOR_USERNAME).isEmpty()) {
            AppUser vendor = new AppUser();
            vendor.setUsername(VENDOR_USERNAME);
            vendor.setPassword(passwordEncoder.encode(PLAIN_PASSWORD));
            vendor.setEmail("ptst_vendor@test.local");
            vendor.setEnabled(true);
            vendor.setVerified(true);
            vendor.setRegistrationDate(LocalDateTime.now());
            vendor.setRoles(Set.of(vendorRole));
            userRepository.save(vendor);
        }
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        userRepository.findByUsername(ADMIN_USERNAME).ifPresent(userRepository::delete);
        userRepository.findByUsername(VENDOR_USERNAME).ifPresent(userRepository::delete);
    }

    /**
     * Creates a fresh MockHttpSession with the security context attribute explicitly null.
     * This prevents {@link HttpSessionSecurityContextRepository} from finding any
     * pre-existing auth context, forcing the authentication filter to run.
     */
    private static MockHttpSession cleanSession() {
        MockHttpSession session = new MockHttpSession();
        // Explicitly null out any previously-stored Spring Security context on this session.
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                null);
        return session;
    }

    // ── Test 1: Admin account rejected by Vendor portal ─────────────────────

    @Test
    @DisplayName("Admin via Vendor portal → denied, redirected to /login?error=admin_portal")
    void adminViaVendorPortal_shouldBeRejected() throws Exception {
        mockMvc.perform(post("/vendor/authenticate")
                        .session(cleanSession())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", ADMIN_USERNAME)
                        .param("password", PLAIN_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=admin_portal"));
    }

    // ── Test 2: Vendor account rejected by Admin portal ─────────────────────

    @Test
    @DisplayName("Vendor via Admin portal → denied, redirected to /admin/login?error=vendor_portal")
    void vendorViaAdminPortal_shouldBeRejected() throws Exception {
        mockMvc.perform(post("/admin/authenticate")
                        .session(cleanSession())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", VENDOR_USERNAME)
                        .param("password", PLAIN_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?error=vendor_portal"));
    }

    // ── Test 3: Admin succeeds via Admin portal ──────────────────────────────

    @Test
    @DisplayName("Admin via Admin portal → success, redirected to /admin/dashboard")
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

    // ── Test 4: Vendor succeeds via Vendor portal ────────────────────────────

    @Test
    @DisplayName("Vendor via Vendor portal → success, redirected to /invoice")
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
}
