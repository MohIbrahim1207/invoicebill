package com.billing.invoicehub;

import com.billing.invoicehub.config.PortalAwareAuthenticationProvider;
import com.billing.invoicehub.entity.AppRole;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.PortalType;
import com.billing.invoicehub.repository.AppRoleRepository;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.service.CustomUserDetailsService;
import com.billing.invoicehub.service.EmailService;
import com.billing.invoicehub.service.EmployeeUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.profiles.active=dev",
        "app.seed.enabled=false",
        "rate-limiting.enabled=false"
})
@Transactional
public class PasswordEncodingAndMigrationTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AppRoleRepository roleRepository;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private EmployeeUserService employeeService;

    @MockBean
    private EmailService emailService;

    private AppRole adminRole;

    @BeforeEach
    void setupRoles() {
        adminRole = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> {
            AppRole r = new AppRole();
            r.setName("ROLE_ADMIN");
            return roleRepository.save(r);
        });
    }

    @Test
    @DisplayName("Requirement 1: DelegatingPasswordEncoder matches un-prefixed BCrypt hash and signals upgrade")
    void testDelegatingPasswordEncoderMatchesUnprefixedBcryptHash() {
        assertInstanceOf(DelegatingPasswordEncoder.class, passwordEncoder,
                "Configured PasswordEncoder should be a DelegatingPasswordEncoder");

        String rawPassword = "LegacyAdminPassword2026!";
        // Produce a legacy un-prefixed BCrypt hash ($2a$ / $2b$ without {bcrypt})
        BCryptPasswordEncoder rawBcrypt = new BCryptPasswordEncoder();
        String legacyHash = rawBcrypt.encode(rawPassword);

        assertFalse(legacyHash.startsWith("{bcrypt}"), "Legacy hash must NOT contain {bcrypt} prefix");
        assertTrue(legacyHash.startsWith("$2a$") || legacyHash.startsWith("$2b$"));

        // Match test
        boolean matches = passwordEncoder.matches(rawPassword, legacyHash);
        assertTrue(matches, "Existing un-prefixed BCrypt hash MUST authenticate successfully");

        // Upgrade test
        assertTrue(passwordEncoder.upgradeEncoding(legacyHash),
                "DelegatingPasswordEncoder should flag legacy un-prefixed hash for upgrade");

        // New encode test
        String newHash = passwordEncoder.encode(rawPassword);
        assertTrue(newHash.startsWith("{bcrypt}"), "New hashes must be prefixed with {bcrypt}");
    }

    @Test
    @DisplayName("Requirement 1: Transparent migration upgrades un-prefixed BCrypt hash to {bcrypt} on successful login")
    void testTransparentPasswordMigrationOnAuthentication() {
        String username = "legacy_admin_test";
        String rawPassword = "SuperSecurePassword123!";

        // Pre-create user with raw un-prefixed BCrypt hash in DB
        BCryptPasswordEncoder rawBcrypt = new BCryptPasswordEncoder();
        String unprefixedHash = rawBcrypt.encode(rawPassword);

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail("legacy_admin@invoicehub.test");
        user.setPassword(unprefixedHash);
        user.setUserDomain("INTERNAL");
        user.setEnabled(true);
        user.setVerified(true);
        user.setRegistrationDate(LocalDateTime.now());
        user.getRoles().add(adminRole);
        userRepository.saveAndFlush(user);

        // Verify initial state has no {bcrypt} prefix
        AppUser savedBefore = userRepository.findFirstByUsername(username).orElseThrow();
        assertFalse(savedBefore.getPassword().startsWith("{bcrypt}"));

        // Authenticate through PortalAwareAuthenticationProvider
        PortalAwareAuthenticationProvider provider = new PortalAwareAuthenticationProvider(
                userDetailsService,
                passwordEncoder,
                userRepository,
                PortalType.ADMIN,
                "INTERNAL",
                Set.of("ROLE_ADMIN"),
                "PORTAL_ERROR:non_admin"
        );

        Authentication authResult = provider.authenticate(
                new UsernamePasswordAuthenticationToken(username, rawPassword)
        );

        assertNotNull(authResult);
        assertTrue(authResult.isAuthenticated());

        // Verify database state: hash must have been transparently upgraded to {bcrypt}
        AppUser savedAfter = userRepository.findFirstByUsername(username).orElseThrow();
        assertTrue(savedAfter.getPassword().startsWith("{bcrypt}"),
                "User password in DB must have been upgraded to {bcrypt} prefix upon successful authentication");
    }

    @Test
    @DisplayName("Requirement 3 & 4: Admin username can be changed from 'admin' to custom handle")
    void testAdminUsernameCanBeChanged() {
        String rawPassword = "AdminPassword2026!";
        String oldUsername = "admin_to_rename_test";
        AppUser admin = new AppUser();
        admin.setUsername(oldUsername);
        admin.setEmail("admin.change@invoicehub.test");
        admin.setFullName("Original Admin");
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setUserDomain("INTERNAL");
        admin.setEnabled(true);
        admin.setVerified(true);
        admin.getRoles().add(adminRole);
        admin = userRepository.saveAndFlush(admin);

        // Update username to custom handle
        AppUser updated = employeeService.updateUser(
                admin.getId(),
                "finance_lead",
                "Original Admin",
                "admin.change@invoicehub.test",
                "ROLE_ADMIN",
                true,
                admin
        );

        assertEquals("finance_lead", updated.getUsername());
        assertTrue(userRepository.findFirstByUsernameAndUserDomain("finance_lead", "INTERNAL").isPresent(),
                "User should be discoverable by new username");
        assertFalse(userRepository.findFirstByUsernameAndUserDomain(oldUsername, "INTERNAL").isPresent(),
                "Old username should no longer exist for this account");
    }

    @Test
    @DisplayName("Requirement 4: Force password change on next login flag can be set and read")
    void testForcePasswordChangeOption() {
        AppUser created = employeeService.createUser(
                "Jane Doe",
                "jane.forced@invoicehub.test",
                "TemporaryPass123!",
                "ROLE_ACCOUNTS",
                true, // forcePasswordChange
                null
        );

        assertTrue(created.isForcePasswordChange(), "Created user must have forcePasswordChange=true");

        // Admin resets password with forcePasswordChange=true
        employeeService.resetPassword(created.getId(), "ResetPass12345!", true, null);
        AppUser reloaded = userRepository.findById(created.getId()).orElseThrow();
        assertTrue(reloaded.isForcePasswordChange(), "Password reset user must retain forcePasswordChange=true");
    }
}
