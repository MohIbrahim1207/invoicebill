package com.billing.invoicehub.controller;

import com.billing.invoicehub.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.PrintWriter;
import java.io.StringWriter;

@RestController
@Profile(value={"dev"})
@RequestMapping(value={"/dev/mail"})
public class DevMailController {
    private final EmailService emailService;
    private final String mailFrom;

    public DevMailController(EmailService emailService, @Value(value="${resend.from.email}") String mailFrom) {
        this.emailService = emailService;
        this.mailFrom = mailFrom;
    }

    @GetMapping(value={"/test"})
    public String sendTestEmail(@RequestParam(value="to") String to) {
        try {
            this.emailService.sendPlainEmail(to, "InvoiceHub - Test Email", "This is a test message from InvoiceHub to verify Resend settings.");
            return "OK: test email sent to " + to;
        }
        catch (Exception ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            return "ERROR: " + ex.getMessage() + "\n\n" + sw.toString();
        }
    }

    @GetMapping(value={"/config"})
    public String config() {
        if (this.mailFrom == null || this.mailFrom.isBlank()) {
            return "resend.from.email is not configured";
        }
        int at = this.mailFrom.indexOf(64);
        if (at <= 1) {
            return "****@" + (at > 0 ? this.mailFrom.substring(at + 1) : "(unknown)");
        }
        String local = this.mailFrom.substring(0, at);
        String domain = this.mailFrom.substring(at + 1);
        String maskedLocal = local.charAt(0) + "***" + local.charAt(local.length() - 1);
        return "resend.from.email=" + maskedLocal + "@" + domain;
    }
}
