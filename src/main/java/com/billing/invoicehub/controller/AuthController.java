/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.config.RoleBasedAuthenticationValidator
 *  com.billing.invoicehub.controller.AuthController
 *  com.billing.invoicehub.dto.VendorRegistrationForm
 *  com.billing.invoicehub.service.VendorRegistrationService
 *  org.springframework.security.authentication.AnonymousAuthenticationToken
 *  org.springframework.security.core.Authentication
 *  org.springframework.security.core.GrantedAuthority
 *  org.springframework.stereotype.Controller
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.ModelAttribute
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.multipart.MultipartFile
 */
package com.billing.invoicehub.controller;

import com.billing.invoicehub.config.RoleBasedAuthenticationValidator;
import com.billing.invoicehub.dto.VendorRegistrationForm;
import com.billing.invoicehub.service.VendorRegistrationService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class AuthController {
    private final VendorRegistrationService vendorRegistrationService;
    private final RoleBasedAuthenticationValidator roleValidator;

    public AuthController(VendorRegistrationService vendorRegistrationService, RoleBasedAuthenticationValidator roleValidator) {
        this.vendorRegistrationService = vendorRegistrationService;
        this.roleValidator = roleValidator;
    }

    @GetMapping(value={"/login"})
    public String login(Authentication authentication) {
        if (this.isAuthenticated(authentication)) {
            return this.hasRole(authentication, "ROLE_ADMIN") ? "redirect:/admin/dashboard" : "redirect:/invoice";
        }
        return "login";
    }

    @GetMapping(value={"/admin/login"})
    public String adminLogin(Authentication authentication) {
        if (this.isAuthenticated(authentication)) {
            return this.hasRole(authentication, "ROLE_ADMIN") ? "redirect:/admin/dashboard" : "redirect:/invoice";
        }
        return "admin-login";
    }

    @GetMapping(value={"/signup", "/register"})
    public String signup(Authentication authentication) {
        if (this.isAuthenticated(authentication)) {
            return "redirect:/invoice";
        }
        return "signup";
    }

    @PostMapping(value={"/register"})
    public String registerUser(@ModelAttribute VendorRegistrationForm form, @RequestParam(value="gstDocument", required=false) MultipartFile gstDocument, @RequestParam(value="companyDocument", required=false) MultipartFile companyDocument, @RequestParam(value="supportingDocument", required=false) MultipartFile supportingDocument) {
        try {
            this.vendorRegistrationService.registerVendor(form, gstDocument, companyDocument, supportingDocument);
            return "redirect:/login?pendingVerification=true";
        }
        catch (IllegalArgumentException ex) {
            return "redirect:/signup?error=" + this.mapRegistrationError(ex.getMessage());
        }
        catch (Exception ex) {
            return "redirect:/signup?error=registration_failed";
        }
    }

    @PostMapping(value={"/signup"})
    public String legacySignupRedirect() {
        return "redirect:/register";
    }

    @GetMapping(value={"/admin/dashboard"})
    public String adminDashboard() {
        return "dashboard";
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(role::equals);
    }

    private String mapRegistrationError(String message) {
        if (message == null) {
            return "invalid_input";
        }
        String normalized = message.toLowerCase();
        if (normalized.contains("exists")) {
            return "username_exists";
        }
        if (normalized.contains("file type")) {
            return "file_type";
        }
        if (normalized.contains("password")) {
            return "password_error";
        }
        return "invalid_input";
    }
}

