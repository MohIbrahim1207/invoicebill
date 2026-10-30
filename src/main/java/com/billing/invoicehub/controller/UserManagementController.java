package com.billing.invoicehub.controller;

import com.billing.invoicehub.entity.AppRole;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.service.CustomUserDetailsService;
import com.billing.invoicehub.service.EmployeeUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.ArrayList;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class UserManagementController {

    private static final Logger log = LoggerFactory.getLogger(UserManagementController.class);

    private final EmployeeUserService userService;
    private final AppUserRepository userRepository;
    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public UserManagementController(EmployeeUserService userService,
                                  AppUserRepository userRepository,
                                  CustomUserDetailsService userDetailsService,
                                  PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/add")
    public String showAddUserForm(Authentication authentication, Model model) {
        AppUser currentUser = getCurrentUser(authentication);
        boolean isSuperAdmin = currentUser != null && currentUser.getRoles().stream()
                .anyMatch(r -> "ROLE_SUPER_ADMIN".equalsIgnoreCase(r.getName()));

        List<AppUser> allUsers = userRepository.findAllByOrderByIdDesc();

        long accountsCount = allUsers.stream().filter(u -> u.getRoles().stream().anyMatch(r -> "ROLE_ACCOUNTS".equalsIgnoreCase(r.getName()))).count();
        long adminCount = allUsers.stream().filter(u -> u.getRoles().stream().anyMatch(r -> "ROLE_ADMIN".equalsIgnoreCase(r.getName()) || "ROLE_SUPER_ADMIN".equalsIgnoreCase(r.getName()))).count();

        model.addAttribute("isSuperAdmin", isSuperAdmin);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("allUsers", allUsers);
        model.addAttribute("accountsCount", accountsCount);
        model.addAttribute("adminCount", adminCount);
        return "admin/users-add";
    }

    @PostMapping("/create")
    public String createUser(@RequestParam("fullName") String fullName,
                             @RequestParam("email") String email,
                             @RequestParam("password") String password,
                             @RequestParam("role") String role,
                             @RequestParam(value = "forcePasswordChange", defaultValue = "false") boolean forcePasswordChange,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = getCurrentUser(authentication);
            AppUser created = userService.createUser(fullName, email, password, role, forcePasswordChange, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "User account for " + created.getEmail() + " (" + role + ") created successfully.");
            return "redirect:/admin/users/add";
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating user: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/users/add";
        }
    }

    @PostMapping("/{id}/update")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> updateUser(@PathVariable("id") Long id,
                                                                 @RequestParam(value = "username", required = false) String username,
                                                                 @RequestParam(value = "fullName", required = false) String fullName,
                                                                 @RequestParam(value = "email", required = false) String email,
                                                                 @RequestParam(value = "role", required = false) String role,
                                                                 @RequestParam(value = "enabled", required = false) Boolean enabled,
                                                                 Authentication authentication) {
        try {
            AppUser currentUser = getCurrentUser(authentication);
            AppUser updated = userService.updateUser(id, username, fullName, email, role, enabled, currentUser);

            // If the currently logged-in admin changed their own username, update the security context
            if (currentUser != null && currentUser.getId().equals(id) && username != null && !username.isBlank()
                    && !username.equalsIgnoreCase(currentUser.getUsername())) {
                try {
                    UserDetails updatedDetails = userDetailsService.loadUserByUsernameAndDomain(updated.getUsername(), updated.getUserDomain());
                    Authentication newAuth = new UsernamePasswordAuthenticationToken(
                            updatedDetails, authentication.getCredentials(), updatedDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(newAuth);
                } catch (Exception ex) {
                    log.warn("Could not refresh security context after username update: {}", ex.getMessage());
                }
            }

            return org.springframework.http.ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "User " + updated.getUsername() + " updated successfully.",
                    "userId", updated.getId(),
                    "username", updated.getUsername(),
                    "fullName", updated.getFullName(),
                    "email", updated.getEmail(),
                    "enabled", updated.isEnabled()
            ));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating user {}: {}", id, e.getMessage(), e);
            return org.springframework.http.ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reset-password")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> resetPassword(@PathVariable("id") Long id,
                                                                    @RequestParam("password") String password,
                                                                    @RequestParam(value = "forcePasswordChange", defaultValue = "false") boolean forcePasswordChange,
                                                                    Authentication authentication) {
        try {
            AppUser currentUser = getCurrentUser(authentication);
            userService.resetPassword(id, password, forcePasswordChange, currentUser);
            return org.springframework.http.ResponseEntity.ok(Map.of("success", true, "message", "Password reset successfully."));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error resetting password for user {}: {}", id, e.getMessage(), e);
            return org.springframework.http.ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/toggle-status")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> toggleUserStatus(@PathVariable("id") Long id,
                                                                       Authentication authentication) {
        try {
            AppUser currentUser = getCurrentUser(authentication);
            boolean newStatus = userService.toggleUserEnabled(id, currentUser);
            return org.springframework.http.ResponseEntity.ok(Map.of(
                    "success", true,
                    "enabled", newStatus,
                    "message", "User account status changed to " + (newStatus ? "ACTIVE" : "DISABLED") + "."
            ));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error toggling user status for ID {}: {}", id, e.getMessage(), e);
            return org.springframework.http.ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> deleteUser(@PathVariable("id") Long id,
                                                                 Authentication authentication) {
        try {
            AppUser currentUser = getCurrentUser(authentication);
            userService.deleteUser(id, currentUser);
            return org.springframework.http.ResponseEntity.ok(Map.of("success", true, "message", "User deleted successfully."));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error deleting user {}: {}", id, e.getMessage(), e);
            return org.springframework.http.ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/audit-weak-passwords")
    @ResponseBody
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public org.springframework.http.ResponseEntity<?> auditWeakPasswords() {
        List<AppUser> users = userRepository.findAll();
        List<Map<String, Object>> flaggedUsers = new ArrayList<>();
        String[] weakPasswords = {"admin123", "vendor123"};

        for (AppUser user : users) {
            String storedHash = user.getPassword();
            if (storedHash == null) continue;
            for (String weak : weakPasswords) {
                if (passwordEncoder.matches(weak, storedHash)) {
                    flaggedUsers.add(Map.of(
                            "id", user.getId(),
                            "username", user.getUsername(),
                            "email", user.getEmail() != null ? user.getEmail() : "",
                            "domain", user.getUserDomain() != null ? user.getUserDomain() : "INTERNAL",
                            "weakDefaultPassword", true,
                            "forcePasswordChange", user.isForcePasswordChange()
                    ));
                    break;
                }
            }
        }

        return org.springframework.http.ResponseEntity.ok(Map.of(
                "totalFlagged", flaggedUsers.size(),
                "users", flaggedUsers
        ));
    }

    @PostMapping("/audit-weak-passwords/force-reset")
    @ResponseBody
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    public org.springframework.http.ResponseEntity<?> forceResetWeakPasswords() {
        List<AppUser> users = userRepository.findAll();
        List<Map<String, Object>> affectedUsers = new ArrayList<>();
        String[] weakPasswords = {"admin123", "vendor123"};
        int resetCount = 0;

        for (AppUser user : users) {
            String storedHash = user.getPassword();
            if (storedHash == null) continue;
            for (String weak : weakPasswords) {
                if (passwordEncoder.matches(weak, storedHash)) {
                    if (!Boolean.TRUE.equals(user.isForcePasswordChange())) {
                        user.setForcePasswordChange(true);
                        userRepository.save(user);
                        resetCount++;
                    }
                    affectedUsers.add(Map.of(
                            "id", user.getId(),
                            "username", user.getUsername(),
                            "email", user.getEmail() != null ? user.getEmail() : "",
                            "domain", user.getUserDomain() != null ? user.getUserDomain() : "INTERNAL",
                            "weakDefaultPassword", true,
                            "forcePasswordChange", true
                    ));
                    break;
                }
            }
        }

        return org.springframework.http.ResponseEntity.ok(Map.of(
                "success", true,
                "totalFlagged", affectedUsers.size(),
                "newlyResetCount", resetCount,
                "users", affectedUsers
        ));
    }

    private AppUser getCurrentUser(Authentication authentication) {
        if (authentication == null) return null;
        return userRepository.findByUsername(authentication.getName())
                .orElseGet(() -> userRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null));
    }
}
