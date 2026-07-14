package com.billing.invoicehub.service;

import com.resend.Resend;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@ActiveProfiles("dev")
@Disabled("Run manually only. Requires a valid RESEND_API_KEY environment variable.")
public class ResendEmailIntegrationTest {

    @Autowired
    private EmailService emailService;

    @Test
    public void testSendHtmlEmailIntegration() {
        assertDoesNotThrow(() -> {
            emailService.sendHtmlEmail(
                "ibrahim12hal@gmail.com",
                "InvoiceHub Integration Test - Resend",
                "<h3>Integration Test Successful</h3><p>This email verifies that InvoiceHub's Resend Integration works as expected!</p>"
            );
        });
    }
}
