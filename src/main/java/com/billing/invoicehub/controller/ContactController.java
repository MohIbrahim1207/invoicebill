/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.controller.ContactController
 *  org.springframework.beans.factory.annotation.Value
 *  org.springframework.mail.SimpleMailMessage
 *  org.springframework.mail.javamail.JavaMailSender
 *  org.springframework.stereotype.Controller
 *  org.springframework.ui.Model
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.servlet.mvc.support.RedirectAttributes
 */
package com.billing.invoicehub.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ContactController {
    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String configuredAdminEmail;

    public ContactController(JavaMailSender mailSender, @Value(value="${spring.mail.username:}") String mailFrom, @Value(value="${app.admin.email:}") String configuredAdminEmail) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.configuredAdminEmail = configuredAdminEmail;
    }

    @GetMapping(value={"/contact"})
    public String contactForm(Model model) {
        return "contact";
    }

    @PostMapping(value={"/contact"})
    public String submitContact(@RequestParam String name, @RequestParam String email, @RequestParam String subject, @RequestParam String message, RedirectAttributes redirectAttributes) {
        String target;
        String string = target = this.configuredAdminEmail != null && !this.configuredAdminEmail.isBlank() ? this.configuredAdminEmail : null;
        if (target == null) {
            redirectAttributes.addFlashAttribute("error", (Object)"No admin email configured to receive contact messages.");
            return "redirect:/contact";
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(target);
            msg.setSubject("Contact Form: " + subject + " (from " + name + ")");
            String body = String.format("Name: %s\nEmail: %s\n\nMessage:\n%s", name, email, message);
            msg.setText(body);
            if (this.mailFrom != null && !this.mailFrom.isBlank()) {
                msg.setFrom(this.mailFrom);
            }
            this.mailSender.send(msg);
            redirectAttributes.addFlashAttribute("message", (Object)"Your message has been sent. We will contact you shortly.");
        }
        catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", (Object)("Failed to send message: " + ex.getMessage()));
        }
        return "redirect:/contact";
    }
}

