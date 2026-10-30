package com.billing.invoicehub;

import com.billing.invoicehub.entity.AppRole;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AppRoleRepository;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.service.CustomUserDetailsService;
import com.billing.invoicehub.service.EmailService;
import com.billing.invoicehub.service.StartupRoleMigration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.profiles.active=dev",
        "app.seed.enabled=false",
        "rate-limiting.enabled=false"
})
@Transactional
public class StartupRoleMigrationDomainSeparationTest {

    @Autowired
    private StartupRoleMigration startupRoleMigration;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AppRoleRepository roleRepository;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @MockBean
    private EmailService emailService;

    private AppRole roleVendor;
    private AppRole roleAdmin;

    @BeforeEach
    void setupRoles() {
        cleanupTestUsers();
        roleVendor = roleRepository.findFirstByName("ROLE_VENDOR").orElseGet(() -> {
            AppRole r = new AppRole();
            r.setName("ROLE_VENDOR");
            return roleRepository.saveAndFlush(r);
        });

        roleAdmin = roleRepository.findFirstByName("ROLE_ADMIN").orElseGet(() -> {
            AppRole r = new AppRole();
            r.setName("ROLE_ADMIN");
            return roleRepository.saveAndFlush(r);
        });
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        cleanupTestUsers();
    }

    private void cleanupTestUsers() {
        try {
            jdbcTemplate.update("DELETE FROM app_user_roles WHERE user_id IN (SELECT id FROM app_user WHERE username LIKE 'legacy_%' OR username LIKE 'attr_%' OR username LIKE 'idemp_%' OR username LIKE 'nonpkp_%')");
            jdbcTemplate.update("DELETE FROM app_user WHERE username LIKE 'legacy_%' OR username LIKE 'attr_%' OR username LIKE 'idemp_%' OR username LIKE 'nonpkp_%'");
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("Legacy vendor accounts with default INTERNAL domain are successfully migrated to VENDOR domain")
    void legacyVendorsWithDefaultInternalDomain_areMigratedToVendorDomain() {
        String username = "legacy_vendor_01";
        String rawPassword = "VendorPassword123!";
        String dynamicGst = "29AABCU" + (System.currentTimeMillis() % 100000) + "R1ZM";

        // 1. Simulate an existing production vendor created before domain separation:
        // Holds ROLE_VENDOR but user_domain was defaulted to INTERNAL
        AppUser vendor = new AppUser();
        vendor.setUsername(username);
        vendor.setEmail("legacy.vendor01@example.com");
        vendor.setPassword(passwordEncoder.encode(rawPassword));
        vendor.setUserDomain("INTERNAL"); // simulated legacy pre-migration state
        vendor.setEnabled(true);
        vendor.setVerified(true);
        vendor.setCompanyName("Legacy Electronics Ltd");
        vendor.setGstNumber(dynamicGst);
        vendor.setRegistrationDate(LocalDateTime.now().minusDays(30));
        vendor.setRoles(new HashSet<>(Set.of(roleVendor)));
        userRepository.saveAndFlush(vendor);

        // Verify that before migration, vendor CANNOT log into VENDOR domain
        assertThrows(UsernameNotFoundException.class, () ->
                userDetailsService.loadUserByUsernameAndDomain(username, "VENDOR"));

        // 2. Execute migration
        entityManager.flush();
        startupRoleMigration.run(null);
        entityManager.clear();

        // 3. Clear persistence context and verify user domain is now VENDOR
        AppUser migrated = userRepository.findFirstByUsername(username).orElseThrow();
        assertEquals("VENDOR", migrated.getUserDomain(), "Vendor domain should be migrated to VENDOR");

        // 4. Verify password and vendor details are preserved
        assertTrue(passwordEncoder.matches(rawPassword, migrated.getPassword()), "Password hash must be preserved");
        assertEquals("Legacy Electronics Ltd", migrated.getCompanyName(), "Company name must be preserved");
        assertEquals(dynamicGst, migrated.getGstNumber(), "GST number must be preserved");

        // 5. Verify vendor can now authenticate in VENDOR domain
        UserDetails details = userDetailsService.loadUserByUsernameAndDomain(username, "VENDOR");
        assertNotNull(details);
        assertEquals(username, details.getUsername());

        // 6. Verify vendor is blocked from INTERNAL domain
        assertThrows(UsernameNotFoundException.class, () ->
                userDetailsService.loadUserByUsernameAndDomain(username, "INTERNAL"));
    }

    @Test
    @DisplayName("Internal admin accounts remain in INTERNAL domain after migration")
    void internalAdminAccounts_remainInInternalDomain() {
        String username = "legacy_admin_01";
        String rawPassword = "AdminPassword123!";

        AppUser admin = new AppUser();
        admin.setUsername(username);
        admin.setEmail("legacy.admin01@example.com");
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setUserDomain("INTERNAL");
        admin.setEnabled(true);
        admin.setVerified(true);
        admin.setRegistrationDate(LocalDateTime.now().minusDays(60));
        admin.setRoles(new HashSet<>(Set.of(roleAdmin)));
        userRepository.saveAndFlush(admin);

        // Run migration
        entityManager.flush();
        startupRoleMigration.run(null);
        entityManager.clear();

        AppUser verified = userRepository.findFirstByUsername(username).orElseThrow();
        assertEquals("INTERNAL", verified.getUserDomain());

        // Admin authenticates in INTERNAL domain
        UserDetails details = userDetailsService.loadUserByUsernameAndDomain(username, "INTERNAL");
        assertNotNull(details);
        assertEquals(username, details.getUsername());

        // Admin blocked from VENDOR domain
        assertThrows(UsernameNotFoundException.class, () ->
                userDetailsService.loadUserByUsernameAndDomain(username, "VENDOR"));
    }

    @Test
    @DisplayName("Accounts with vendor profile attributes but unassigned domain are classified as VENDOR")
    void vendorProfileAttributes_fallbackClassification() {
        String username = "attr_vendor_01";

        AppUser vendor = new AppUser();
        vendor.setUsername(username);
        vendor.setEmail("attr.vendor01@example.com");
        vendor.setPassword(passwordEncoder.encode("Password123!"));
        vendor.setUserDomain("INTERNAL");
        vendor.setEnabled(true);
        vendor.setVerified(true);
        vendor.setCompanyName("Attribute Discovered Vendor");
        vendor.setGstNumber("33AABCU" + (System.currentTimeMillis() % 100000 + 1) + "R1ZK");
        vendor.setRegistrationDate(LocalDateTime.now().minusDays(10));
        // No roles attached directly
        userRepository.saveAndFlush(vendor);

        // Run migration
        entityManager.flush();
        startupRoleMigration.run(null);
        entityManager.clear();

        AppUser verified = userRepository.findFirstByUsername(username).orElseThrow();
        assertEquals("VENDOR", verified.getUserDomain(), "Should be classified as VENDOR based on GST / Company profile");
    }

    @Test
    @DisplayName("Migration is strictly idempotent and does not delete or mutate unchanged records")
    void migrationIsStrictlyIdempotent() {
        long initialCount = userRepository.count();

        // Seed 3 vendors and 2 admins
        for (int i = 1; i <= 3; i++) {
            AppUser v = new AppUser();
            v.setUsername("idemp_vendor_" + i);
            v.setEmail("idemp.vendor" + i + "@example.com");
            v.setPassword(passwordEncoder.encode("SecretPass" + i));
            v.setUserDomain("INTERNAL"); // pre-migration
            v.setEnabled(true);
            v.setVerified(true);
            v.setRoles(new HashSet<>(Set.of(roleVendor)));
            userRepository.save(v);
        }

        for (int i = 1; i <= 2; i++) {
            AppUser a = new AppUser();
            a.setUsername("idemp_admin_" + i);
            a.setEmail("idemp.admin" + i + "@example.com");
            a.setPassword(passwordEncoder.encode("SecretAdminPass" + i));
            a.setUserDomain("INTERNAL");
            a.setEnabled(true);
            a.setVerified(true);
            a.setRoles(new HashSet<>(Set.of(roleAdmin)));
            userRepository.save(a);
        }
        userRepository.flush();

        long seededCount = userRepository.count();
        assertEquals(initialCount + 5, seededCount);

        // Run migration first time
        entityManager.flush();
        assertDoesNotThrow(() -> startupRoleMigration.run(null));
        entityManager.clear();

        // Verify count is preserved exactly (no accounts deleted)
        assertEquals(seededCount, userRepository.count());

        // Run migration second time (idempotency check)
        entityManager.flush();
        assertDoesNotThrow(() -> startupRoleMigration.run(null));
        entityManager.clear();
        assertEquals(seededCount, userRepository.count());

        // Verify all 3 vendors still have VENDOR domain and intact passwords
        for (int i = 1; i <= 3; i++) {
            AppUser v = userRepository.findFirstByUsername("idemp_vendor_" + i).orElseThrow();
            assertEquals("VENDOR", v.getUserDomain());
            assertTrue(passwordEncoder.matches("SecretPass" + i, v.getPassword()));
        }

        // Verify both admins still have INTERNAL domain and intact passwords
        for (int i = 1; i <= 2; i++) {
            AppUser a = userRepository.findFirstByUsername("idemp_admin_" + i).orElseThrow();
            assertEquals("INTERNAL", a.getUserDomain());
            assertTrue(passwordEncoder.matches("SecretAdminPass" + i, a.getPassword()));
        }
    }

    @Test
    @DisplayName("VendorRegistrationService.normalizeGstNumber converts non-PKP placeholders and empty strings to null")
    void testNormalizeGstNumber() {
        assertNull(com.billing.invoicehub.service.VendorRegistrationService.normalizeGstNumber(null));
        assertNull(com.billing.invoicehub.service.VendorRegistrationService.normalizeGstNumber(""));
        assertNull(com.billing.invoicehub.service.VendorRegistrationService.normalizeGstNumber("   "));
        assertNull(com.billing.invoicehub.service.VendorRegistrationService.normalizeGstNumber("-"));
        assertNull(com.billing.invoicehub.service.VendorRegistrationService.normalizeGstNumber("--"));
        assertNull(com.billing.invoicehub.service.VendorRegistrationService.normalizeGstNumber("0"));
        assertNull(com.billing.invoicehub.service.VendorRegistrationService.normalizeGstNumber("00"));
        assertNull(com.billing.invoicehub.service.VendorRegistrationService.normalizeGstNumber("No PPN"));
        assertNull(com.billing.invoicehub.service.VendorRegistrationService.normalizeGstNumber("NO PPN"));
        assertNull(com.billing.invoicehub.service.VendorRegistrationService.normalizeGstNumber("NON PKP"));
        assertNull(com.billing.invoicehub.service.VendorRegistrationService.normalizeGstNumber("non-pkp"));
        assertNull(com.billing.invoicehub.service.VendorRegistrationService.normalizeGstNumber("NA"));
        assertNull(com.billing.invoicehub.service.VendorRegistrationService.normalizeGstNumber("N/A"));
        assertNull(com.billing.invoicehub.service.VendorRegistrationService.normalizeGstNumber("NONE"));
        assertNull(com.billing.invoicehub.service.VendorRegistrationService.normalizeGstNumber("Tidak Ada"));

        // Valid GST remains uppercase string
        assertEquals("012345678901000", com.billing.invoicehub.service.VendorRegistrationService.normalizeGstNumber(" 012345678901000 "));
        assertEquals("29AABCU9603R1ZM", com.billing.invoicehub.service.VendorRegistrationService.normalizeGstNumber("29aabcu9603r1zm"));
    }

    @Test
    @DisplayName("StartupRoleMigration normalizes non-PKP GST placeholders in DB to NULL")
    void testNonPkpPlaceholdersNormalizedToNullDuringStartupMigration() {
        String[] placeholders = {"-", "0", "No PPN", "NON PKP", "   "};
        for (int i = 0; i < placeholders.length; i++) {
            AppUser u = new AppUser();
            u.setUsername("nonpkp_user_" + i);
            u.setEmail("nonpkp" + i + "@example.com");
            u.setPassword(passwordEncoder.encode("SecretPass" + i));
            u.setUserDomain("VENDOR");
            u.setEnabled(true);
            u.setGstNumber(placeholders[i]);
            u.setRoles(new HashSet<>(Set.of(roleVendor)));
            userRepository.save(u);
        }
        userRepository.flush();

        // Run migration
        entityManager.flush();
        assertDoesNotThrow(() -> startupRoleMigration.run(null));
        entityManager.clear();

        // Verify all have null gstNumber now
        for (int i = 0; i < placeholders.length; i++) {
            AppUser u = userRepository.findFirstByUsername("nonpkp_user_" + i).orElseThrow();
            assertNull(u.getGstNumber(), "Placeholder '" + placeholders[i] + "' should have been normalized to null");
        }
    }
}
