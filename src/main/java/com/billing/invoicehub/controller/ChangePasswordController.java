package com.billing.invoicehub.controller;

import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ChangePasswordController {

    private static final Logger log = LoggerFactory.getLogger(ChangePasswordController.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public ChangePasswordController(AppUserRepository userRepository,
                                  PasswordEncoder passwordEncoder,
                                  AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @GetMapping({"/change-password", "/admin/change-password"})
    public String showChangePasswordForm(Authentication authentication, Model model, HttpServletRequest request) {
        if (!isAuthenticated(authentication)) {
            String path = request.getRequestURI();
            return path.contains("/admin") ? "redirect:/admin/login" : "redirect:/vendor/login";
        }

        AppUser user = getCurrentUser(authentication);
        if (user == null) {
            return "redirect:/logout";
        }

        boolean isAdmin = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(r -> r.equals("ROLE_ADMIN") || r.equals("ROLE_SUPER_ADMIN") || r.equals("ROLE_ACCOUNTS"));

        model.addAttribute("username", user.getUsername());
        model.addAttribute("email", user.getEmail());
        model.addAttribute("forced", user.isForcePasswordChange());
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("postUrl", isAdmin ? "/admin/change-password" : "/change-password");

        return "change-password";
    }

    @PostMapping({"/change-password", "/admin/change-password"})
    public String processPasswordChange(@RequestParam("currentPassword") String currentPassword,
                                        @RequestParam("newPassword") String newPassword,
                                        @RequestParam("confirmPassword") String confirmPassword,
                                        Authentication authentication,
                                        HttpServletRequest request,
                                        HttpSession session,
                                        Model model,
                                        RedirectAttributes redirectAttributes) {
        if (!isAuthenticated(authentication)) {
            return "redirect:/login";
        }

        AppUser user = getCurrentUser(authentication);
        if (user == null) {
            return "redirect:/logout";
        }

        boolean isAdmin = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(r -> r.equals("ROLE_ADMIN") || r.equals("ROLE_SUPER_ADMIN") || r.equals("ROLE_ACCOUNTS"));

        model.addAttribute("username", user.getUsername());
        model.addAttribute("email", user.getEmail());
        model.addAttribute("forced", user.isForcePasswordChange());
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("postUrl", isAdmin ? "/admin/change-password" : "/change-password");

        if (currentPassword == null || currentPassword.isBlank()) {
            model.addAttribute("errorKey", "validation.currentPassword.required");
            model.addAttribute("errorMessage", "Current password is required.");
            return "change-password";
        }

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            model.addAttribute("errorKey", "validation.currentPassword.incorrect");
            model.addAttribute("errorMessage", "Current password is incorrect.");
            return "change-password";
        }

        if (newPassword == null || newPassword.isBlank()) {
            model.addAttribute("errorKey", "validation.password.required");
            model.addAttribute("errorMessage", "New password is required.");
            return "change-password";
        }

        if (newPassword.equals(currentPassword)) {
            model.addAttribute("errorKey", "validation.password.sameAsCurrent");
            model.addAttribute("errorMessage", "New password cannot be the same as your current password.");
            return "change-password";
        }

        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("errorKey", "validation.confirmPassword.mismatch");
            model.addAttribute("errorMessage", "New passwords do not match.");
            return "change-password";
        }

        if (newPassword.length() < 6) {
            model.addAttribute("errorKey", "validation.password.size");
            model.addAttribute("errorMessage", "Password must be at least 6 characters long.");
            return "change-password";
        }

        // Update password with modern encoder and clear forced change flag
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setForcePasswordChange(false);
        userRepository.save(user);

        session.removeAttribute("FORCE_PASSWORD_CHANGE");

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .reduce((a, b) -> a + "," + b).orElse("ROLE_USER");
        auditLogService.log(user.getUsername(), role, "PASSWORD_CHANGED", "AppUser", user.getId(), null, "User updated their password successfully");

        redirectAttributes.addFlashAttribute("successMessage", "Password updated successfully.");
        return isAdmin ? "redirect:/admin/dashboard" : "redirect:/invoice";
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private AppUser getCurrentUser(Authentication authentication) {
        if (authentication == null) return null;
        return userRepository.findFirstByUsername(authentication.getName())
                .orElseGet(() -> userRepository.findFirstByEmailIgnoreCase(authentication.getName()).orElse(null));
    }
}

