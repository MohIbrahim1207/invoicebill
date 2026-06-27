/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.entity.AppRole
 *  com.billing.invoicehub.entity.AppUser
 *  com.billing.invoicehub.repository.AppRoleRepository
 *  com.billing.invoicehub.repository.AppUserRepository
 *  com.billing.invoicehub.service.RegistrationService
 *  org.springframework.security.crypto.password.PasswordEncoder
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 */
package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.AppRole;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AppRoleRepository;
import com.billing.invoicehub.repository.AppUserRepository;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private final AppUserRepository userRepository;
    private final AppRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(AppUserRepository userRepository, AppRoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public boolean registerUser(String username, String email, String password, String confirmPassword) {
        if (!this.isValidUsername(username) || !this.isValidEmail(email) || password == null || password.length() < 6) {
            return false;
        }
        if (!password.equals(confirmPassword)) {
            return false;
        }
        if (this.userRepository.findByUsername(username).isPresent()) {
            return false;
        }
        AppUser newUser = new AppUser();
        newUser.setUsername(username);
        newUser.setEmail(email);
        newUser.setPassword(this.passwordEncoder.encode((CharSequence)password));
        newUser.setEnabled(true);
        Optional userRole = this.roleRepository.findByName("ROLE_VENDOR");
        if (userRole.isPresent()) {
            newUser.setRoles(Set.of((AppRole)userRole.get()));
        }
        this.userRepository.save(newUser);
        return true;
    }

    private boolean isValidUsername(String username) {
        if (username == null) {
            return false;
        }
        return username.matches("^[a-zA-Z0-9]{3,20}$");
    }

    private boolean isValidEmail(String email) {
        if (email == null) {
            return false;
        }
        return email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    public boolean userExists(String username) {
        return this.userRepository.findByUsername(username).isPresent();
    }
}

