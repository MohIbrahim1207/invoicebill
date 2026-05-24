/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.controller.ForgotPasswordController
 *  com.billing.invoicehub.service.PasswordResetService
 *  jakarta.servlet.http.HttpSession
 *  org.springframework.stereotype.Controller
 *  org.springframework.ui.Model
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.servlet.mvc.support.RedirectAttributes
 */
package com.billing.invoicehub.controller;

import com.billing.invoicehub.service.PasswordResetService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ForgotPasswordController {
    private final PasswordResetService passwordResetService;

    public ForgotPasswordController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @GetMapping(value={"/forgot-password"})
    public String forgotPassword() {
        return "forgot-password";
    }

    @PostMapping(value={"/forgot-password"})
    public String sendOtp(@RequestParam String username, @RequestParam String email, Model model, RedirectAttributes redirectAttributes) {
        String result = this.passwordResetService.sendOtpToEmail(username, email);
        if ("OK".equals(result)) {
            redirectAttributes.addFlashAttribute("email", (Object)email);
            return "redirect:/verify-otp";
        }
        if ("USERNAME_NOT_FOUND".equals(result)) {
            model.addAttribute("error", (Object)"Username not found");
            return "forgot-password";
        }
        if ("EMAIL_MISMATCH".equals(result)) {
            model.addAttribute("error", (Object)"Email does not match this username");
            return "forgot-password";
        }
        model.addAttribute("error", (Object)"Failed to send OTP. Please try again later.");
        return "forgot-password";
    }

    @GetMapping(value={"/verify-otp"})
    public String verifyOtpPage(@RequestParam(required=false) String email, Model model) {
        if (email != null && !email.isBlank()) {
            model.addAttribute("email", (Object)email);
        }
        return "verify-otp";
    }

    @PostMapping(value={"/verify-otp"})
    public String verifyOtp(@RequestParam String email, @RequestParam String otp, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        boolean verified = this.passwordResetService.verifyOtp(email, otp);
        if (!verified) {
            model.addAttribute("email", (Object)email);
            model.addAttribute("error", (Object)"Invalid or expired OTP. Please try again.");
            return "verify-otp";
        }
        session.setAttribute("resetEmail", (Object)email);
        redirectAttributes.addFlashAttribute("email", (Object)email);
        return "redirect:/reset-password";
    }

    @GetMapping(value={"/reset-password"})
    public String resetPasswordPage(HttpSession session, Model model) {
        Object email = session.getAttribute("resetEmail");
        if (email == null) {
            return "redirect:/forgot-password";
        }
        model.addAttribute("email", email);
        return "reset-password";
    }

    @PostMapping(value={"/reset-password"})
    public String resetPassword(@RequestParam String email, @RequestParam String newPassword, @RequestParam String confirmPassword, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Object sessionEmail = session.getAttribute("resetEmail");
        if (sessionEmail == null || !sessionEmail.equals(email)) {
            redirectAttributes.addFlashAttribute("error", (Object)"Session expired. Please try again.");
            return "redirect:/forgot-password";
        }
        if (newPassword == null || newPassword.trim().isEmpty()) {
            model.addAttribute("email", (Object)email);
            model.addAttribute("error", (Object)"Password cannot be empty.");
            return "reset-password";
        }
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("email", (Object)email);
            model.addAttribute("error", (Object)"Passwords do not match.");
            return "reset-password";
        }
        boolean updated = this.passwordResetService.resetPassword(email, newPassword, confirmPassword);
        if (updated) {
            session.removeAttribute("resetEmail");
            redirectAttributes.addFlashAttribute("message", (Object)"Password updated successfully. Please log in.");
            return "redirect:/login";
        }
        model.addAttribute("email", (Object)email);
        model.addAttribute("error", (Object)"Failed to update password. Please try again.");
        return "reset-password";
    }
}

