package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.AppRole;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.NotificationType;
import com.billing.invoicehub.repository.AppRoleRepository;
import com.billing.invoicehub.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmployeeUserService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeUserService.class);

    private final AppUserRepository userRepository;
    private final AppRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public EmployeeUserService(AppUserRepository userRepository,
                               AppRoleRepository roleRepository,
                               PasswordEncoder passwordEncoder,
                               AuditLogService auditLogService,
                               NotificationService notificationService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    @Transactional
    public AppUser createUser(String fullName, String email, String rawPassword, String requestedRole, boolean forcePasswordChange, AppUser creator) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Company email address is required.");
        }
        String cleanEmail = email.trim().toLowerCase();

        // Check if user already exists in INTERNAL domain
        if (userRepository.existsByEmailIgnoreCaseAndUserDomain(cleanEmail, "INTERNAL")
                || userRepository.existsByUsernameAndUserDomain(cleanEmail, "INTERNAL")
                || userRepository.findFirstByEmailIgnoreCaseAndUserDomain(cleanEmail, "INTERNAL").isPresent()
                || userRepository.findFirstByUsernameAndUserDomain(cleanEmail, "INTERNAL").isPresent()) {
            throw new IllegalArgumentException("A user with this email address already exists in the system.");
        }

        // Hard-stop validation: block if email already exists under VENDOR domain
        if (userRepository.existsByEmailIgnoreCaseAndUserDomain(cleanEmail, "VENDOR")
                || userRepository.existsByUsernameAndUserDomain(cleanEmail, "VENDOR")
                || userRepository.findFirstByEmailIgnoreCaseAndUserDomain(cleanEmail, "VENDOR").isPresent()) {
            throw new IllegalArgumentException("This email is already registered as a Vendor account. Each email may only be associated with one portal domain.");
        }

        // Validate Role Assignment Security Restrictions
        boolean isCreatorSuperAdmin = creator != null && creator.getRoles().stream()
                .anyMatch(r -> "ROLE_SUPER_ADMIN".equalsIgnoreCase(r.getName()));

        String normalizedRole = requestedRole.startsWith("ROLE_") ? requestedRole : "ROLE_" + requestedRole;

        if (!isCreatorSuperAdmin && ("ROLE_ADMIN".equalsIgnoreCase(normalizedRole) || "ROLE_SUPER_ADMIN".equalsIgnoreCase(normalizedRole))) {
            log.warn("Unauthorized attempt by user {} to assign restricted role {}", creator != null ? creator.getUsername() : "UNKNOWN", normalizedRole);
            throw new AccessDeniedException("Only Super Admins can assign ADMIN or SUPER_ADMIN roles.");
        }

        AppRole roleEntity = roleRepository.findFirstByName(normalizedRole)
                .orElseGet(() -> {
                    AppRole newRole = new AppRole();
                    newRole.setName(normalizedRole);
                    return roleRepository.save(newRole);
                });

        AppUser user = new AppUser();
        user.setUserDomain("INTERNAL");
        user.setUsername(cleanEmail);
        user.setEmail(cleanEmail);
        user.setFullName(fullName != null ? fullName.trim() : cleanEmail);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setEnabled(true);
        user.setVerified(true);
        user.setForcePasswordChange(forcePasswordChange);
        user.setRegistrationDate(LocalDateTime.now());
        user.getRoles().add(roleEntity);

        AppUser saved = userRepository.save(user);

        // Audit Log
        String creatorRoles = creator != null ? creator.getRoles().stream().map(AppRole::getName).collect(Collectors.joining(",")) : "ROLE_ADMIN";
        String creatorUsername = creator != null ? creator.getUsername() : "SYSTEM";
        auditLogService.log(creatorUsername, creatorRoles, "USER_CREATED", "AppUser", saved.getId(), "NONE", normalizedRole);

        // Notification
        notificationService.createNotification(
                "Welcome to InvoiceHub",
                "Your account (" + cleanEmail + ") has been created with role " + normalizedRole + ".",
                NotificationType.INFO,
                saved
        );

        return saved;
    }

    public AppUser createUser(String fullName, String email, String rawPassword, String requestedRole, AppUser creator) {
        return createUser(fullName, email, rawPassword, requestedRole, false, creator);
    }

    @Transactional(readOnly = true)
    public List<AppUser> getUsersWithRole(String roleName) {
        String normalized = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
        return userRepository.findByRoles_NameOrderByIdDesc(normalized);
    }

    @Transactional
    public AppUser updateUser(Long userId, String username, String fullName, String email, String requestedRole, Boolean enabled, AppUser actor) {
        AppUser target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        checkSuperAdminProtection(target, actor, "edit");

        // Allow changing the username so administrative accounts aren't stuck with default 'admin'
        if (username != null && !username.isBlank()) {
            String cleanUsername = username.trim();
            if (!cleanUsername.equalsIgnoreCase(target.getUsername())) {
                if (userRepository.findFirstByUsernameAndUserDomain(cleanUsername, target.getUserDomain()).isPresent()) {
                    throw new IllegalArgumentException("Username '" + cleanUsername + "' is already in use in the " + target.getUserDomain() + " portal.");
                }
                target.setUsername(cleanUsername);
            }
        }

        if (email != null && !email.isBlank()) {
            String cleanEmail = email.trim().toLowerCase();
            if (!cleanEmail.equalsIgnoreCase(target.getEmail())) {
                if (userRepository.existsByEmailIgnoreCaseAndUserDomain(cleanEmail, target.getUserDomain())) {
                    throw new IllegalArgumentException("Email address " + cleanEmail + " is already in use.");
                }
                target.setEmail(cleanEmail);
                if (username == null || username.isBlank()) {
                    target.setUsername(cleanEmail);
                }
            }
        }

        if (fullName != null && !fullName.isBlank()) {
            target.setFullName(fullName.trim());
        }

        if (enabled != null) {
            target.setEnabled(enabled);
        }

        if (requestedRole != null && !requestedRole.isBlank()) {
            String normalizedRole = requestedRole.startsWith("ROLE_") ? requestedRole : "ROLE_" + requestedRole;
            boolean alreadyHasRole = target.getRoles().stream()
                    .anyMatch(r -> normalizedRole.equalsIgnoreCase(r.getName()));

            if (!alreadyHasRole) {
                boolean isSuperAdmin = actor != null && actor.getRoles().stream()
                        .anyMatch(r -> "ROLE_SUPER_ADMIN".equalsIgnoreCase(r.getName()));

                if (!isSuperAdmin && ("ROLE_ADMIN".equalsIgnoreCase(normalizedRole) || "ROLE_SUPER_ADMIN".equalsIgnoreCase(normalizedRole))) {
                    throw new AccessDeniedException("Only Super Admins can assign ADMIN or SUPER_ADMIN roles.");
                }

                AppRole roleEntity = roleRepository.findFirstByName(normalizedRole)
                        .orElseGet(() -> {
                            AppRole newRole = new AppRole();
                            newRole.setName(normalizedRole);
                            return roleRepository.save(newRole);
                        });

                target.getRoles().clear();
                target.getRoles().add(roleEntity);
            }
        }

        AppUser updated = userRepository.save(target);
        String actorUsername = actor != null ? actor.getUsername() : "SYSTEM";
        auditLogService.log(actorUsername, "ADMIN", "USER_UPDATED", "AppUser", updated.getId(), "UPDATED", "Updated user details");
        return updated;
    }

    public AppUser updateUser(Long userId, String fullName, String email, String requestedRole, Boolean enabled, AppUser actor) {
        return updateUser(userId, null, fullName, email, requestedRole, enabled, actor);
    }

    @Transactional
    public void resetPassword(Long userId, String newPassword, boolean forcePasswordChange, AppUser actor) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long.");
        }
        AppUser target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        checkSuperAdminProtection(target, actor, "reset the password of");

        target.setPassword(passwordEncoder.encode(newPassword));
        target.setForcePasswordChange(forcePasswordChange);
        userRepository.save(target);

        String actorUsername = actor != null ? actor.getUsername() : "SYSTEM";
        auditLogService.log(actorUsername, "ADMIN", "USER_PASSWORD_RESET", "AppUser", target.getId(), "PASSWORD_RESET", "Reset password for user");
    }

    public void resetPassword(Long userId, String newPassword, AppUser actor) {
        resetPassword(userId, newPassword, false, actor);
    }

    @Transactional
    public boolean toggleUserEnabled(Long userId, AppUser actor) {
        AppUser target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        checkSuperAdminProtection(target, actor, "modify");

        boolean newStatus = !target.isEnabled();
        target.setEnabled(newStatus);
        userRepository.save(target);

        String actorUsername = actor != null ? actor.getUsername() : "SYSTEM";
        auditLogService.log(actorUsername, "ADMIN", "USER_STATUS_TOGGLED", "AppUser", target.getId(), "ENABLED_STATUS", String.valueOf(newStatus));
        return newStatus;
    }

    @Transactional
    public void deleteUser(Long userId, AppUser actor) {
        AppUser target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        checkSuperAdminProtection(target, actor, "delete");

        if (actor != null && actor.getId().equals(userId)) {
            throw new IllegalArgumentException("You cannot delete your own account.");
        }

        userRepository.delete(target);
        String actorUsername = actor != null ? actor.getUsername() : "SYSTEM";
        auditLogService.log(actorUsername, "ADMIN", "USER_DELETED", "AppUser", userId, "DELETED", target.getEmail());
    }

    private void checkSuperAdminProtection(AppUser target, AppUser actor, String actionDescription) {
        if (target != null && target.getRoles() != null) {
            boolean isTargetSuperAdmin = target.getRoles().stream()
                    .anyMatch(r -> "ROLE_SUPER_ADMIN".equalsIgnoreCase(r.getName()));
            if (isTargetSuperAdmin) {
                boolean isActorSuperAdmin = actor != null && actor.getRoles() != null && actor.getRoles().stream()
                        .anyMatch(r -> "ROLE_SUPER_ADMIN".equalsIgnoreCase(r.getName()));
                if (!isActorSuperAdmin) {
                    throw new AccessDeniedException("Only a SUPER_ADMIN can " + actionDescription + " a SUPER_ADMIN account.");
                }
            }
        }
    }
}
