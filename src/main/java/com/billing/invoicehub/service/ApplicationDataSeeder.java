/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.entity.AppRole
 *  com.billing.invoicehub.entity.AppUser
 *  com.billing.invoicehub.repository.AppRoleRepository
 *  com.billing.invoicehub.repository.AppUserRepository
 *  com.billing.invoicehub.service.ApplicationDataSeeder
 *  org.springframework.boot.CommandLineRunner
 *  org.springframework.security.crypto.password.PasswordEncoder
 *  org.springframework.stereotype.Component
 *  org.springframework.transaction.annotation.Transactional
 */
package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.AppRole;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AppRoleRepository;
import com.billing.invoicehub.repository.AppUserRepository;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = false)
public class ApplicationDataSeeder
implements CommandLineRunner {
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_VENDOR = "ROLE_VENDOR";
    private static final Logger log = LoggerFactory.getLogger(ApplicationDataSeeder.class);
    private final AppRoleRepository roleRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ApplicationDataSeeder(AppRoleRepository roleRepository, AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void run(String ... args) {
        try {
            this.ensureRole(ROLE_ADMIN);
            this.ensureRole(ROLE_VENDOR);
            // Ensure role rows are committed before writing app_user_roles join rows.
            this.roleRepository.flush();

            this.ensureUser("admin", "admin123", Set.of(ROLE_ADMIN), true);
            this.ensureUser("vendor", "vendor123", Set.of(ROLE_VENDOR), true);
            log.info("Default user seeding completed.");
        } catch (RuntimeException ex) {
            // Seeder should never block application startup in non-empty databases.
            log.error("Default user seeding failed; startup will continue. Cause: {}", ex.getMessage(), ex);
        }
    }

    private AppRole ensureRole(String roleName) {
        return this.roleRepository.findByName(roleName).orElseGet(() -> {
            AppRole role = new AppRole();
            role.setName(roleName);
            return this.roleRepository.saveAndFlush(role);
        });
    }

    private void ensureUser(String username, String rawPassword, Set<String> roleNames, boolean verified) {
        Set<AppRole> roles = new HashSet<>();
        for (String roleName : roleNames) {
            AppRole role = this.roleRepository.findByName(roleName).orElseThrow(() -> new IllegalStateException("Role not found: " + roleName));
            roles.add(role);
        }

        this.userRepository.findByUsername(username).ifPresentOrElse(
            user -> {
                // User exists, update roles if needed
                Set<AppRole> currentRoles = user.getRoles();
                for (AppRole role : roles) {
                    currentRoles.add(role);
                }
                user.setRoles(currentRoles);
                this.userRepository.saveAndFlush(user);
            },
            () -> {
                // User doesn't exist, create new user
                AppUser user = new AppUser();
                user.setUsername(username);
                user.setPassword(this.passwordEncoder.encode((CharSequence)rawPassword));
                user.setEnabled(true);
                user.setRoles(roles);
                user.setVerified(verified);
                this.userRepository.saveAndFlush(user);
            }
        );
    }
}

