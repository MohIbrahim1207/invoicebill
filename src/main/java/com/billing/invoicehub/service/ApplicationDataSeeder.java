package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.AppRole;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AppRoleRepository;
import com.billing.invoicehub.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = false)
public class ApplicationDataSeeder implements CommandLineRunner {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_VENDOR = "ROLE_VENDOR";
    private static final Logger log = LoggerFactory.getLogger(ApplicationDataSeeder.class);

    private final AppRoleRepository roleRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.initial-password:${APP_ADMIN_INITIAL_PASSWORD:${APP_ADMIN_PASSWORD:}}}")
    private String initialAdminPassword;

    @Value("${app.admin.initial-username:${APP_ADMIN_INITIAL_USERNAME:${APP_ADMIN_USERNAME:admin}}}")
    private String initialAdminUsername;

    @Value("${app.admin.initial-email:${APP_ADMIN_INITIAL_EMAIL:${APP_ADMIN_EMAIL:}}}")
    private String initialAdminEmail;

    @Value("${app.vendor.initial-password:${APP_VENDOR_INITIAL_PASSWORD:}}")
    private String initialVendorPassword;

    public ApplicationDataSeeder(AppRoleRepository roleRepository,
                                 AppUserRepository userRepository,
                                 PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        try {
            ensureRole(ROLE_ADMIN);
            ensureRole(ROLE_VENDOR);
            this.roleRepository.flush();

            // Requirement 3: Only seed admin when no admin exists, read from environment variable,
            // set forcePasswordChange=true, and never use hardcoded credentials.
            boolean adminExists = userRepository.findAll().stream().anyMatch(u ->
                    u.getRoles() != null && u.getRoles().stream().anyMatch(r ->
                            "ROLE_ADMIN".equalsIgnoreCase(r.getName()) || "ROLE_SUPER_ADMIN".equalsIgnoreCase(r.getName())
                    )
            );

            if (!adminExists) {
                String adminUsername = (initialAdminUsername != null && !initialAdminUsername.isBlank())
                        ? initialAdminUsername.trim() : "admin";
                String adminPassword = initialAdminPassword;

                if (adminPassword == null || adminPassword.isBlank()) {
                    adminPassword = generateSecureTemporaryPassword();
                    log.warn("================================================================================");
                    log.warn("[SECURITY NOTICE] No initial admin password supplied in environment variable.");
                    log.warn("Generated temporary admin password for '{}': {}", adminUsername, adminPassword);
                    log.warn("Account has forcePasswordChange=true. You must change it upon first login.");
                    log.warn("================================================================================");
                }

                String adminEmail = (initialAdminEmail != null && !initialAdminEmail.isBlank())
                        ? initialAdminEmail.trim().toLowerCase() : adminUsername + "@invoicehub.cloud";

                AppUser admin = new AppUser();
                admin.setUserDomain("INTERNAL");
                admin.setUsername(adminUsername);
                admin.setEmail(adminEmail);
                admin.setFullName("System Administrator");
                admin.setPassword(this.passwordEncoder.encode(adminPassword));
                admin.setEnabled(true);
                admin.setVerified(true);
                admin.setForcePasswordChange(true); // Forced change on next login
                admin.setRegistrationDate(LocalDateTime.now());
                admin.getRoles().add(ensureRole(ROLE_ADMIN));
                this.userRepository.saveAndFlush(admin);
                log.info("Initial admin account seeded successfully with forcePasswordChange=true.");
            } else {
                log.info("Admin account already exists in database. Skipping admin seeding.");
            }

            // Vendor seeding: only when configured via environment variable and no vendor exists
            if (initialVendorPassword != null && !initialVendorPassword.isBlank()) {
                boolean vendorExists = userRepository.findAll().stream().anyMatch(u ->
                        u.getRoles() != null && u.getRoles().stream().anyMatch(r ->
                                "ROLE_VENDOR".equalsIgnoreCase(r.getName())
                        )
                );
                if (!vendorExists) {
                    AppUser vendor = new AppUser();
                    vendor.setUserDomain("VENDOR");
                    vendor.setUsername("vendor");
                    vendor.setEmail("vendor@invoicehub.cloud");
                    vendor.setCompanyName("Demo Vendor Inc");
                    vendor.setFullName("Demo Vendor");
                    vendor.setPassword(this.passwordEncoder.encode(initialVendorPassword));
                    vendor.setEnabled(true);
                    vendor.setVerified(true);
                    vendor.setForcePasswordChange(true);
                    vendor.setRegistrationDate(LocalDateTime.now());
                    vendor.getRoles().add(ensureRole(ROLE_VENDOR));
                    this.userRepository.saveAndFlush(vendor);
                    log.info("Demo vendor seeded with forcePasswordChange=true.");
                }
            }
        } catch (RuntimeException ex) {
            log.error("Default user seeding check failed; startup will continue. Cause: {}", ex.getMessage(), ex);
        }
    }

    private AppRole ensureRole(String roleName) {
        return this.roleRepository.findByName(roleName).orElseGet(() -> {
            AppRole role = new AppRole();
            role.setName(roleName);
            return this.roleRepository.saveAndFlush(role);
        });
    }

    private String generateSecureTemporaryPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
