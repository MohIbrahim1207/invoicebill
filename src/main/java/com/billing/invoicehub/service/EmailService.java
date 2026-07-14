package com.billing.invoicehub.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final Resend resend;
    private final SpringTemplateEngine templateEngine;
    private final String fromEmail;
    private final String replyToEmail;
    private final String adminEmail;

    public EmailService(Resend resend,
            SpringTemplateEngine templateEngine,
            @Value("${resend.from.email}") String fromEmail,
            @Value("${resend.reply-to}") String replyToEmail,
            @Value("${app.admin.email}") String adminEmail) {
        this.resend = resend;
        this.templateEngine = templateEngine;
        this.fromEmail = fromEmail;
        this.replyToEmail = replyToEmail;
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
        sendHtmlEmailInternal(to, subject, htmlContent, true);
    }

    public void sendPlainEmail(String to, String subject, String textContent) {
        sendHtmlEmailInternal(to, subject, textContent, false);
    }

    private void sendHtmlEmailInternal(String to, String subject, String content, boolean isHtml) {
        if (to == null || to.isBlank()) {
            log.warn("Skipping email '{}' because recipient address is missing", subject);
            return;
        }

        CreateEmailOptions.Builder optionsBuilder = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(to)
                .subject(subject)
                .replyTo(replyToEmail);

        if (isHtml) {
            optionsBuilder.html(content);
        } else {
            optionsBuilder.text(content);
        }

        CreateEmailOptions options = optionsBuilder.build();

        int maxAttempts = 2;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.info("Sending email to {} with subject '{}' (Attempt {}/{})", to, subject, attempt, maxAttempts);
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
            // Usually ResendException or HTTP response errors.
            // Timeout/Network issues manifest as general IOExceptions or 5xx Status codes.
            // Validation errors such as invalid email structure/format (400 Bad Request) or authorization (401/403) should not be retried.
            // For safety, let's look at the exception message/cause.
            String msg = ex.getMessage().toLowerCase();
            if (msg.contains("validation") || msg.contains("invalid") || msg.contains("400") || msg.contains("401") || msg.contains("403")) {
                return false;
            }
        }
        return true;
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