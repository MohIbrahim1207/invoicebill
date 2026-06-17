package com.billing.invoicehub.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final String fromEmail;
    private final String adminEmail;

    public EmailService(JavaMailSender mailSender,
            SpringTemplateEngine templateEngine,
            @Value("${spring.mail.username:}") String fromEmail,
            @Value("${app.admin.email:admin@invoicehub.com}") String adminEmail) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.fromEmail = fromEmail;
        this.adminEmail = adminEmail;
    }

    public void sendVendorApprovalEmail(String to, String vendorName, String vendorCode) {
        String subject = "InvoiceHub - Account Verified";
        Context context = new Context(Locale.getDefault());
        context.setVariable("vendorName", safe(vendorName));
        context.setVariable("vendorCode", safe(vendorCode));
        String html = renderTemplate("email/vendor-approved", context);
        sendHtmlEmail(to, subject, html);
    }

    public void sendVendorRejectionEmail(String to, String vendorName, String reason) {
        String subject = "InvoiceHub - Registration Rejected";
        Context context = new Context(Locale.getDefault());
        context.setVariable("vendorName", safe(vendorName));
        context.setVariable("reason", safe(reason));
        String html = renderTemplate("email/vendor-rejected", context);
        sendHtmlEmail(to, subject, html);
    }

    public void sendRegistrationReceivedEmail(String to, String vendorName, String companyName) {
        String subject = "InvoiceHub - Registration Received";
        Context context = new Context(Locale.getDefault());
        context.setVariable("vendorName", safe(vendorName));
        context.setVariable("companyName", safe(companyName));
        String html = renderTemplate("email/registration-received", context);
        sendHtmlEmail(to, subject, html);
    }

    public void sendPasswordResetOtpEmail(String to, String otp) {
        String subject = "InvoiceHub - Password Reset OTP";
        Context context = new Context(Locale.getDefault());
        context.setVariable("otp", safe(otp));
        String html = renderTemplate("email/password-reset-otp", context);
        sendHtmlEmail(to, subject, html);
    }

    public void sendEmailToAdmin(String subject, String htmlContent) {
        sendHtmlEmail(adminEmail, subject, htmlContent);
    }

    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        if (to == null || to.isBlank()) {
            log.warn("Skipping email '{}' because recipient address is missing", subject);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email sent successfully to {} with subject '{}'", to, subject);
        } catch (MessagingException ex) {
            log.error("Failed to build email '{}' for {}: {}", subject, to, ex.getMessage(), ex);
        } catch (Exception ex) {
            log.error("Failed to send email '{}' to {}: {}", subject, to, ex.getMessage(), ex);
        }
    }

    public void sendTicketSubmittedEmail(String to,
            String vendorName,
            String ticketNo,
            String invoiceNo,
            String invoiceDate,
            String clientName,
            String poNumber,
            String subtotal,
            String tax,
            String total,
            String viewUrl) {
        String subject = "InvoiceHub - Ticket Submitted: " + safe(ticketNo);
        Context context = new Context(Locale.getDefault());
        context.setVariable("vendorName", safe(vendorName));
        context.setVariable("ticketNo", safe(ticketNo));
        context.setVariable("invoiceNo", safe(invoiceNo));
        context.setVariable("invoiceDate", safe(invoiceDate));
        context.setVariable("clientName", safe(clientName));
        context.setVariable("poNumber", safe(poNumber));
        context.setVariable("subtotal", safe(subtotal));
        context.setVariable("tax", safe(tax));
        context.setVariable("total", safe(total));
        context.setVariable("viewUrl", safe(viewUrl));
        String html = renderTemplate("email/ticket-submitted", context);
        sendHtmlEmail(to, subject, html);
    }

    public void sendTicketCancelledEmail(String to,
            String vendorName,
            String ticketNo,
            String invoiceNo,
            String reason) {
        String subject = "InvoiceHub - Ticket Cancelled: " + safe(ticketNo);
        Context context = new Context(Locale.getDefault());
        context.setVariable("vendorName", safe(vendorName));
        context.setVariable("ticketNo", safe(ticketNo));
        context.setVariable("invoiceNo", safe(invoiceNo));
        context.setVariable("reason", safe(reason));
        String html = renderTemplate("email/ticket-cancelled", context);
        sendHtmlEmail(to, subject, html);
    }

    public void sendInvoiceStatusEmail(String to, String invoiceNumber, String vendorName, String newStatus,
            String amount, String statusDate, String adminRemarks, String viewUrl) {
        String subject = "InvoiceHub - Invoice Status Updated: " + safe(newStatus);
        Context context = new Context(Locale.getDefault());
        context.setVariable("invoiceNumber", safe(invoiceNumber));
        context.setVariable("vendorName", safe(vendorName));
        context.setVariable("newStatus", safe(newStatus));
        context.setVariable("amount", safe(amount));
        context.setVariable("statusDate", safe(statusDate));
        context.setVariable("adminRemarks", adminRemarks == null || adminRemarks.isBlank() ? null : safe(adminRemarks));
        context.setVariable("viewUrl", safe(viewUrl));
        String html = renderTemplate("email/invoice-status-update", context);
        sendHtmlEmail(to, subject, html);
    }

    private String renderTemplate(String templateName, Context context) {
        try {
            return templateEngine.process(templateName, context);
        } catch (Exception ex) {
            log.error("Failed to render email template {}: {}", templateName, ex.getMessage(), ex);
            return "<html><body><p>Email content unavailable.</p></body></html>";
        }
    }

    private String safe(String value) {
        if (value == null) {
            return "-";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}