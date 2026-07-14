package com.billing.invoicehub.controller;

import com.billing.invoicehub.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ContactController {
    private final EmailService emailService;
    private final String configuredAdminEmail;

    public ContactController(EmailService emailService, @Value(value="${app.admin.email}") String configuredAdminEmail) {
        this.emailService = emailService;
        this.configuredAdminEmail = configuredAdminEmail;
    }

    @GetMapping(value={"/contact"})
    public String contactForm(Model model) {
        return "contact";
    }

    @PostMapping(value={"/contact"})
    public String submitContact(@RequestParam String name, @RequestParam String email, @RequestParam String subject, @RequestParam String message, RedirectAttributes redirectAttributes) {
        if (this.configuredAdminEmail == null || this.configuredAdminEmail.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "No admin email configured to receive contact messages.");
            return "redirect:/contact";
        }
        try {
            String body = String.format("Name: %s\nEmail: %s\n\nMessage:\n%s", name, email, message);
            this.emailService.sendPlainEmail(this.configuredAdminEmail, "Contact Form: " + subject + " (from " + name + ")", body);
            redirectAttributes.addFlashAttribute("message", "Your message has been sent. We will contact you shortly.");
        }
        catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Failed to send message: " + ex.getMessage());
        }
        return "redirect:/contact";
    }
}
