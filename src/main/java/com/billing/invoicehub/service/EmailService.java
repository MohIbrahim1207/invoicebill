/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.service.EmailService
 *  jakarta.mail.MessagingException
 *  jakarta.mail.internet.MimeMessage
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.annotation.Value
 *  org.springframework.mail.SimpleMailMessage
 *  org.springframework.mail.javamail.JavaMailSender
 *  org.springframework.mail.javamail.MimeMessageHelper
 *  org.springframework.stereotype.Service
 */
package com.billing.invoicehub.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;
    @Value(value="${spring.mail.username:noreply@invoicehub.com}")
    private String fromEmail;
    @Value(value="${app.admin.email:admin@invoicehub.com}")
    private String adminEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendSimpleEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(this.fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            this.mailSender.send(message);
            log.info("Simple email sent to: {}", (Object)to);
        }
        catch (Exception e) {
            log.error("Failed to send simple email to {}: {}", (Object)to, (Object)e.getMessage());
        }
    }

    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = this.mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(this.fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            this.mailSender.send(message);
            log.info("HTML email sent to: {}", (Object)to);
        }
        catch (MessagingException e) {
            log.error("Failed to send HTML email to {}: {}", (Object)to, (Object)e.getMessage());
        }
        catch (Exception e) {
            log.error("Unexpected error sending email to {}: {}", (Object)to, (Object)e.getMessage());
        }
    }

    public void sendHtmlEmailToMultiple(String[] recipients, String subject, String htmlContent) {
        try {
            for (String recipient : recipients) {
                this.sendHtmlEmail(recipient, subject, htmlContent);
            }
        }
        catch (Exception e) {
            log.error("Failed to send HTML email to multiple recipients: {}", (Object)e.getMessage());
        }
    }

    public void sendEmailToAdmin(String subject, String htmlContent) {
        this.sendHtmlEmail(this.adminEmail, subject, htmlContent);
    }

    public String generateEmailTemplate(String templateName, String ... params) {
        return "<html><body>" + templateName + "</body></html>";
    }
}

