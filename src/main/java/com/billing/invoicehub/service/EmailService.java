package com.billing.invoicehub.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final Resend resend;
    private final SpringTemplateEngine templateEngine;
    private final EmailSettingService emailSettingService;
    private final String fromEmail;
    private final String replyToEmail;
    private final String adminEmail;
    private final String appBaseUrl;

    public EmailService(Resend resend,
            SpringTemplateEngine templateEngine,
            @Autowired(required = false) EmailSettingService emailSettingService,
            @Value("${resend.from.email}") String fromEmail,
            @Value("${resend.reply-to}") String replyToEmail,
            @Value("${app.admin.email}") String adminEmail,
            @Value("${app.base-url:https://invoicehub.cloud}") String appBaseUrl) {
        this.resend = resend;
        this.templateEngine = templateEngine;
        this.emailSettingService = emailSettingService;
        this.fromEmail = fromEmail;
        this.replyToEmail = replyToEmail;
        this.adminEmail = adminEmail;
        this.appBaseUrl = appBaseUrl;
    }

    public String getLoginUrl() {
        String base = (appBaseUrl != null && !appBaseUrl.isBlank()) ? appBaseUrl.trim() : "https://invoicehub.cloud";
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/login";
    }

    public List<String> resolveNotificationCc(String recipient) {
        if (emailSettingService != null) {
            return emailSettingService.getNotificationCcRecipients(recipient);
        }
        return Collections.emptyList();
    }

    // ─── Operational Notification Emails (Includes Login Link & CC Routing) ───

    public void sendVendorApprovalEmail(String to, String vendorName, String vendorCode) {
        String subject = "InvoiceHub - Account Verified";
        Context context = new Context(Locale.getDefault());
        context.setVariable("vendorName", safe(vendorName));
        context.setVariable("vendorCode", safe(vendorCode));
        context.setVariable("loginUrl", getLoginUrl());
        String html = renderTemplate("email/vendor-approved", context);
        sendNotificationHtmlEmail(to, subject, html);
    }

    public void sendVendorRejectionEmail(String to, String vendorName, String reason) {
        String subject = "InvoiceHub - Registration Rejected";
        Context context = new Context(Locale.getDefault());
        context.setVariable("vendorName", safe(vendorName));
        context.setVariable("reason", safe(reason));
        context.setVariable("loginUrl", getLoginUrl());
        String html = renderTemplate("email/vendor-rejected", context);
        sendNotificationHtmlEmail(to, subject, html);
    }

    public void sendRegistrationReceivedEmail(String to, String vendorName, String companyName) {
        String subject = "InvoiceHub - Registration Received";
        Context context = new Context(Locale.getDefault());
        context.setVariable("vendorName", safe(vendorName));
        context.setVariable("companyName", safe(companyName));
        context.setVariable("loginUrl", getLoginUrl());
        String html = renderTemplate("email/registration-received", context);
        sendNotificationHtmlEmail(to, subject, html);
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
        context.setVariable("loginUrl", getLoginUrl());
        String html = renderTemplate("email/ticket-submitted", context);
        sendNotificationHtmlEmail(to, subject, html);
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
        context.setVariable("loginUrl", getLoginUrl());
        String html = renderTemplate("email/ticket-cancelled", context);
        sendNotificationHtmlEmail(to, subject, html);
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
        context.setVariable("loginUrl", getLoginUrl());
        String html = renderTemplate("email/invoice-status-update", context);
        sendNotificationHtmlEmail(to, subject, html);
    }

    @org.springframework.scheduling.annotation.Async
    public void sendVendorTicketSubmittedAdminEmail(String to, String ticketNo, String vendorName, String vendorCode, String companyName, String subjectText, String priority, String submissionDate, String viewUrl) {
        String subject = "New Vendor Ticket Submitted - " + safe(ticketNo);
        Context context = new Context(Locale.getDefault());
        context.setVariable("ticketNo", safe(ticketNo));
        context.setVariable("vendorName", safe(vendorName));
        context.setVariable("vendorCode", safe(vendorCode));
        context.setVariable("companyName", safe(companyName));
        context.setVariable("subjectText", safe(subjectText));
        context.setVariable("priority", safe(priority));
        context.setVariable("submissionDate", safe(submissionDate));
        context.setVariable("viewUrl", safe(viewUrl));
        context.setVariable("loginUrl", getLoginUrl());
        String html = renderTemplate("email/vendor-ticket-submitted-admin", context);
        sendNotificationHtmlEmail(to, subject, html);
    }

    // ─── Security Sensitive Emails (STRICTLY NO CC - User Only) ─────────────────

    public void sendPasswordResetOtpEmail(String to, String otp) {
        String subject = "InvoiceHub - Password Reset OTP";
        Context context = new Context(Locale.getDefault());
        context.setVariable("otp", safe(otp));
        context.setVariable("loginUrl", getLoginUrl());
        String html = renderTemplate("email/password-reset-otp", context);
        sendSecurityHtmlEmail(to, subject, html);
    }

    // ─── Low-level Dispatchers ──────────────────────────────────────────────────

    public void sendNotificationHtmlEmail(String to, String subject, String htmlContent) {
        List<String> cc = resolveNotificationCc(to);
        sendHtmlEmailInternal(to, subject, htmlContent, true, cc);
    }

    public void sendSecurityHtmlEmail(String to, String subject, String htmlContent) {
        sendHtmlEmailInternal(to, subject, htmlContent, true, Collections.emptyList());
    }

    public void sendEmailToAdmin(String subject, String htmlContent) {
        sendNotificationHtmlEmail(adminEmail, subject, htmlContent);
    }

    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        sendHtmlEmailInternal(to, subject, htmlContent, true, Collections.emptyList());
    }

    public void sendPlainEmail(String to, String subject, String textContent) {
        sendHtmlEmailInternal(to, subject, textContent, false, Collections.emptyList());
    }

    public void sendHtmlEmailWithCc(String to, String subject, String htmlContent, List<String> ccList) {
        sendHtmlEmailInternal(to, subject, htmlContent, true, ccList);
    }

    private void sendHtmlEmailInternal(String to, String subject, String content, boolean isHtml, List<String> ccList) {
        if (to == null || to.isBlank()) {
            log.warn("Skipping email '{}' because recipient address is missing", subject);
            return;
        }

        CreateEmailOptions.Builder optionsBuilder = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(to)
                .subject(subject)
                .replyTo(replyToEmail);

        String copyMode = (emailSettingService != null) ? emailSettingService.getNotificationCopyMode() : "BCC";

        if (ccList != null && !ccList.isEmpty()) {
            if ("CC".equalsIgnoreCase(copyMode)) {
                optionsBuilder.cc(ccList);
            } else {
                optionsBuilder.bcc(ccList);
            }
        }

        if (isHtml) {
            optionsBuilder.html(content);
        } else {
            optionsBuilder.text(content);
        }

        CreateEmailOptions options = optionsBuilder.build();

        int maxAttempts = 2;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (ccList != null && !ccList.isEmpty()) {
                    log.info("Sending email to {} (mode: {}, copyList: {}) with subject '{}' (Attempt {}/{})", to, copyMode, ccList, subject, attempt, maxAttempts);
                } else {
                    log.info("Sending email to {} with subject '{}' (Attempt {}/{})", to, subject, attempt, maxAttempts);
                }
                CreateEmailResponse response = resend.emails().send(options);
                if (response != null && response.getId() != null) {
                    log.info("Email sent successfully via Resend to {} with subject '{}', ID: {}", to, subject, response.getId());
                    return;
                } else {
                    throw new RuntimeException("Resend API response was empty or ID is null");
                }
            } catch (Exception ex) {
                log.warn("Failed to send email on attempt {}/{}: {}", attempt, maxAttempts, ex.getMessage());
                if (attempt < maxAttempts && isTransientError(ex)) {
                    try {
                        log.info("Retrying transient email sending error in 500ms...");
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Retry delay interrupted: {}", ie.getMessage());
                        break;
                    }
                } else {
                    log.error("Failed to send email to {} with subject '{}' after {} attempts: {}", to, subject, attempt, ex.getMessage(), ex);
                    break;
                }
            }
        }
    }

    private boolean isTransientError(Exception ex) {
        if (ex instanceof ResendException) {
            String msg = ex.getMessage().toLowerCase();
            if (msg.contains("validation") || msg.contains("invalid") || msg.contains("400") || msg.contains("401") || msg.contains("403")) {
                return false;
            }
        }
        return true;
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
