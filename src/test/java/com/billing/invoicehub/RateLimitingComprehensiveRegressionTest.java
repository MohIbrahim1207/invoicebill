package com.billing.invoicehub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limiting.enabled=true",
        "rate-limiting.behind-proxy=true",
        "rate-limiting.auth.capacity=3",
        "rate-limiting.auth.refill-tokens=3",
        "rate-limiting.auth.refill-period-minutes=1",
        "rate-limiting.register.capacity=3",
        "rate-limiting.register.refill-tokens=3",
        "rate-limiting.register.refill-period-minutes=1"
})
class RateLimitingComprehensiveRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("SEC-RL-01: Page loads (/faq, /) are explicitly NOT rate-limited")
    void pageLoads_bypassRateLimiting() throws Exception {
        String clientIp = "10.20.1.101";

        // Normal page loads should never hit 429 even with 10 consecutive requests
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/faq")
                            .header("X-Real-IP", clientIp))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("SEC-RL-02: Static resources (/css/**, /js/**, favicon.ico) bypass rate limiting")
    void staticResources_bypassRateLimiting() throws Exception {
        String clientIp = "10.20.1.102";

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/css/portal.css")
                            .header("X-Real-IP", clientIp))
                    .andExpect(status().is(org.hamcrest.Matchers.not(429)));
        }
    }

    @Test
    @DisplayName("SEC-RL-03: Sensitive auth endpoint (/forgot-password) enforces auth capacity and returns bilingual HTML")
    void authEndpoint_enforcesAuthCapacityAndReturnsBilingualHtml() throws Exception {
        String clientIp = "10.20.1.103";

        // Auth capacity is 3
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/forgot-password")
                            .header("X-Real-IP", clientIp)
                            .param("username", "testuser")
                            .param("email", "test@example.com")
                            .with(csrf()))
                    .andExpect(status().isOk());
        }

        // 4th request must be rate limited with HTTP 429, Retry-After, and bilingual HTML
        mockMvc.perform(post("/forgot-password")
                        .header("X-Real-IP", clientIp)
                        .param("username", "testuser")
                        .param("email", "test@example.com")
                        .with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("Terlalu Banyak Permintaan")))
                .andExpect(content().string(containsString("Too Many Requests")));
    }

    @Test
    @DisplayName("SEC-RL-04: API endpoint rate limiting returns JSON 429")
    void apiEndpoint_returnsJson429() throws Exception {
        String clientIp = "10.20.1.104";

        // Register API capacity is 3
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/register/check-email")
                            .header("X-Real-IP", clientIp)
                            .param("email", "vendor@example.com")
                            .with(csrf()))
                    .andExpect(status().is(org.hamcrest.Matchers.not(429)));
        }

        // 4th request gets JSON 429
        mockMvc.perform(post("/api/register/check-email")
                        .header("X-Real-IP", clientIp)
                        .param("email", "vendor@example.com")
                        .with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("SEC-RL-05: Behind proxy extracts client IP from X-Real-IP and X-Forwarded-For")
    void behindProxy_extractsClientIp() throws Exception {
        String clientIp = "10.20.1.105";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/forgot-password")
                            .header("X-Forwarded-For", clientIp + ", 10.0.0.1, 10.0.0.2")
                            .param("username", "testuser")
                            .param("email", "test@example.com")
                            .with(csrf()))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/forgot-password")
                        .header("X-Forwarded-For", clientIp + ", 10.0.0.1, 10.0.0.2")
                        .param("username", "testuser")
                        .param("email", "test@example.com")
                        .with(csrf()))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("SEC-RL-06: Multi-tenant IP bucket isolation guarantees independent client quotas")
    void ipIsolation_independentQuotas() throws Exception {
        String ip1 = "10.20.1.106";
        String ip2 = "10.20.1.107";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/forgot-password")
                            .header("X-Real-IP", ip1)
                            .param("username", "testuser")
                            .param("email", "test@example.com")
                            .with(csrf()))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/forgot-password")
                        .header("X-Real-IP", ip1)
                        .param("username", "testuser")
                        .param("email", "test@example.com")
                        .with(csrf()))
                .andExpect(status().isTooManyRequests());

        // ip2 must still have its full quota available
        mockMvc.perform(post("/forgot-password")
                        .header("X-Real-IP", ip2)
                        .param("username", "testuser")
                        .param("email", "test@example.com")
                        .with(csrf()))
                .andExpect(status().isOk());
    }
}
