/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.controller.DevMailController
 *  org.springframework.beans.factory.annotation.Value
 *  org.springframework.mail.SimpleMailMessage
 *  org.springframework.mail.javamail.JavaMailSender
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.bind.annotation.RestController
 */
package com.billing.invoicehub.controller;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile(value={"dev"})
@RequestMapping(value={"/dev/mail"})
public class DevMailController {
    private final JavaMailSender mailSender;
    private final String mailFrom;

    public DevMailController(JavaMailSender mailSender, @Value(value="${spring.mail.username:}") String mailFrom) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
    }

    @GetMapping(value={"/test"})
    public String sendTestEmail(@RequestParam(value="to") String to) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("InvoiceHub - Test Email");
            message.setText("This is a test message from InvoiceHub to verify SMTP settings.");
            if (this.mailFrom != null && !this.mailFrom.isBlank()) {
                message.setFrom(this.mailFrom);
            }
            this.mailSender.send(message);
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
            return "mail.username is not configured";
        }
        int at = this.mailFrom.indexOf(64);
        if (at <= 1) {
            return "****@" + (at > 0 ? this.mailFrom.substring(at + 1) : "(unknown)");
        }
        String local = this.mailFrom.substring(0, at);
        String domain = this.mailFrom.substring(at + 1);
        String maskedLocal = local.charAt(0) + "***" + local.charAt(local.length() - 1);
        return "mail.username=" + maskedLocal + "@" + domain;
    }
}

