package com.billing.invoicehub.controller;

import com.billing.invoicehub.entity.AppRole;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.service.EmployeeUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class UserManagementController {

    private static final Logger log = LoggerFactory.getLogger(UserManagementController.class);

    private final EmployeeUserService userService;
    private final AppUserRepository userRepository;

    public UserManagementController(EmployeeUserService userService, AppUserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
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
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = getCurrentUser(authentication);
            AppUser created = userService.createUser(fullName, email, password, role, currentUser);
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
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<?> updateUser(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                                                 @RequestParam(value = "fullName", required = false) String fullName,
                                                                 @RequestParam(value = "email", required = false) String email,
                                                                 @RequestParam(value = "role", required = false) String role,
                                                                 @RequestParam(value = "enabled", required = false) Boolean enabled,
                                                                 Authentication authentication) {
        try {
            AppUser currentUser = getCurrentUser(authentication);
            AppUser updated = userService.updateUser(id, fullName, email, role, enabled, currentUser);
            return org.springframework.http.ResponseEntity.ok(java.util.Map.of(
                    "success", true,
                    "message", "User " + updated.getEmail() + " updated successfully.",
                    "userId", updated.getId(),
                    "fullName", updated.getFullName(),
                    "email", updated.getEmail(),
                    "enabled", updated.isEnabled()
            ));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating user {}: {}", id, e.getMessage(), e);
            return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reset-password")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<?> resetPassword(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                                                    @RequestParam("password") String password,
                                                                    Authentication authentication) {
        try {
            AppUser currentUser = getCurrentUser(authentication);
            userService.resetPassword(id, password, currentUser);
            return org.springframework.http.ResponseEntity.ok(java.util.Map.of("success", true, "message", "Password reset successfully."));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error resetting password for user {}: {}", id, e.getMessage(), e);
            return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/toggle-status")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<?> toggleUserStatus(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                                                       Authentication authentication) {
        try {
            AppUser currentUser = getCurrentUser(authentication);
            boolean newStatus = userService.toggleUserEnabled(id, currentUser);
            return org.springframework.http.ResponseEntity.ok(java.util.Map.of(
                    "success", true,
                    "enabled", newStatus,
                    "message", "User account status changed to " + (newStatus ? "ACTIVE" : "DISABLED") + "."
            ));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error toggling user status for ID {}: {}", id, e.getMessage(), e);
            return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<?> deleteUser(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                                                 Authentication authentication) {
        try {
            AppUser currentUser = getCurrentUser(authentication);
            userService.deleteUser(id, currentUser);
            return org.springframework.http.ResponseEntity.ok(java.util.Map.of("success", true, "message", "User deleted successfully."));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error deleting user {}: {}", id, e.getMessage(), e);
            return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    private AppUser getCurrentUser(Authentication authentication) {
        if (authentication == null) return null;
        return userRepository.findByUsername(authentication.getName())
                .orElseGet(() -> userRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null));
    }
}
