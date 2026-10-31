package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AppUserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Helper service to access current user info (initials, avatar, email, role badge)
 * in templates and controllers cleanly.
 */
@Service
@Transactional(readOnly = true)
public class CurrentUserService {

    private final AppUserRepository userRepository;

    public CurrentUserService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<AppUser> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return userRepository.findFirstByUsername(auth.getName())
                .or(() -> userRepository.findFirstByEmailIgnoreCase(auth.getName()));
    }

    public String getInitials() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return "U";
        }
        Optional<AppUser> userOpt = getCurrentUser();
        if (userOpt.isPresent()) {
            AppUser user = userOpt.get();
            if (user.getFullName() != null && !user.getFullName().isBlank()) {
                String[] parts = user.getFullName().trim().split("\\s+");
                if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                    return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
                } else if (!parts[0].isEmpty() && parts[0].length() >= 2) {
                    return parts[0].substring(0, 2).toUpperCase();
                } else if (!parts[0].isEmpty()) {
                    return parts[0].substring(0, 1).toUpperCase();
                }
            }
            if (user.getUsername() != null && user.getUsername().length() >= 2) {
                return user.getUsername().substring(0, 2).toUpperCase();
            }
        }
        String name = auth.getName();
        if (name != null && name.length() >= 2) {
            return name.substring(0, 2).toUpperCase();
        }
        return "AD";
    }

    public String getEmail() {
        return getCurrentUser()
                .map(AppUser::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .orElseGet(() -> {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    return auth != null ? auth.getName() : "";
                });
    }

    public String getPrimaryRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "USER";
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String role = ga.getAuthority();
            if ("ROLE_SUPER_ADMIN".equals(role)) return "SUPER_ADMIN";
            if ("ROLE_ADMIN".equals(role)) return "ADMIN";
            if ("ROLE_ACCOUNTS".equals(role)) return "ACCOUNTS";
            if ("ROLE_VENDOR".equals(role)) return "VENDOR";
        }
        return "USER";
    }

    public String getRoleBadgeLabel() {
        String role = getPrimaryRole();
        switch (role) {
            case "SUPER_ADMIN": return "Super Admin";
            case "ADMIN": return "Admin";
            case "ACCOUNTS": return "Finance";
            case "VENDOR": return "Vendor";
            default: return "User";
        }
    }
}

