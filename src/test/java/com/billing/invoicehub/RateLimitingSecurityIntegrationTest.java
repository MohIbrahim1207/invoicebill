package com.billing.invoicehub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limiting.enabled=true",
        "rate-limiting.contact.capacity=3",
        "rate-limiting.contact.refill-tokens=3",
        "rate-limiting.contact.refill-period-minutes=1"
})
class RateLimitingSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("SEC-001: Standard requests exceeding capacity are rate-limited with HTTP 429")
    void normalRequests_shouldBeRateLimited() throws Exception {
        String testIp = "10.0.0.1";

        // Consume capacity (3 allowed)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/contact")
                            .header("X-Forwarded-For", testIp)
                            .param("name", "Test")
                            .param("email", "test@example.com")
                            .param("subject", "Test Subject")
                            .param("message", "Test message")
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }

        // 4th request MUST be rate limited with HTTP 429
        mockMvc.perform(post("/contact")
                        .header("X-Forwarded-For", testIp)
                        .param("name", "Test")
                        .param("email", "test@example.com")
                        .param("subject", "Test Subject")
                        .param("message", "Test message")
                        .with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Too many requests. Please try again later."));
    }

    @Test
    @DisplayName("SEC-001: Requests with X-Bypass-Rate-Limit header MUST still be rate-limited (bypass removed)")
    void requestsWithBypassHeader_mustStillBeRateLimited() throws Exception {
        String testIp = "10.0.0.2";

        // Consume capacity (3 allowed) even when header is sent
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/contact")
                            .header("X-Forwarded-For", testIp)
                            .header("X-Bypass-Rate-Limit", "true")
                            .param("name", "Attacker")
                            .param("email", "attacker@example.com")
                            .param("subject", "Exploit Attempt")
                            .param("message", "Attempting rate limit bypass")
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }

        // 4th request with X-Bypass-Rate-Limit: true MUST STILL be blocked with HTTP 429
        mockMvc.perform(post("/contact")
                        .header("X-Forwarded-For", testIp)
                        .header("X-Bypass-Rate-Limit", "true")
                        .param("name", "Attacker")
                        .param("email", "attacker@example.com")
                        .param("subject", "Exploit Attempt")
                        .param("message", "Attempting rate limit bypass")
                        .with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Too many requests. Please try again later."));
    }
}
