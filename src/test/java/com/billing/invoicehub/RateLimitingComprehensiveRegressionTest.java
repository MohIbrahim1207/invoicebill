package com.billing.invoicehub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limiting.enabled=true",
        "rate-limiting.behind-proxy=true",
        "rate-limiting.global.capacity=5",
        "rate-limiting.global.refill-tokens=5",
        "rate-limiting.global.refill-period-minutes=1",
        "rate-limiting.auth.capacity=3",
        "rate-limiting.auth.refill-tokens=3",
        "rate-limiting.auth.refill-period-minutes=1"
})
class RateLimitingComprehensiveRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("SEC-RL-01: Global rate limiting enforces capacity on dynamic endpoints (/faq)")
    void globalRateLimiting_enforcesCapacity() throws Exception {
        String clientIp = "10.20.1.101";

        // Consume all 5 allowed global tokens
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/faq")
                            .header("X-Real-IP", clientIp))
                    .andExpect(status().isOk());
        }

        // 6th request must be rate limited with HTTP 429
        mockMvc.perform(get("/faq")
                        .header("X-Real-IP", clientIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Too many requests. Please try again later."));
    }

    @Test
    @DisplayName("SEC-RL-02: Static resources (/css/**, /js/**, favicon.ico) bypass rate limiting")
    void staticResources_bypassRateLimiting() throws Exception {
        String clientIp = "10.20.1.102";

        // Send 10 requests to a static resource (well over global capacity of 5)
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/css/portal.css")
                            .header("X-Real-IP", clientIp))
                    // Should pass through filter (status might be 200 or 404 depending on mock file, but NOT 429)
                    .andExpect(status().is(org.hamcrest.Matchers.not(429)));
        }

        // Even after 10 static requests, dynamic endpoint must still have its fresh capacity
        mockMvc.perform(get("/faq")
                        .header("X-Real-IP", clientIp))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SEC-RL-03: Sensitive auth endpoint (/forgot-password) enforces stricter auth capacity")
    void authEndpoint_enforcesAuthCapacity() throws Exception {
        String clientIp = "10.20.1.103";

        // Auth capacity is set to 3
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/forgot-password")
                            .header("X-Real-IP", clientIp)
                            .param("username", "testuser")
                            .param("email", "test@example.com")
                            .with(csrf()))
                    .andExpect(status().isOk()); // Returns view with error/message, not 429
        }

        // 4th request must be blocked with HTTP 429
        mockMvc.perform(post("/forgot-password")
                        .header("X-Real-IP", clientIp)
                        .param("username", "testuser")
                        .param("email", "test@example.com")
                        .with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("SEC-RL-04: Header bypass attempts (X-Bypass-Rate-Limit) are rejected")
    void bypassHeader_isIgnoredAndRateLimited() throws Exception {
        String clientIp = "10.20.1.104";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/faq")
                            .header("X-Real-IP", clientIp)
                            .header("X-Bypass-Rate-Limit", "true"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/faq")
                        .header("X-Real-IP", clientIp)
                        .header("X-Bypass-Rate-Limit", "true"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("SEC-RL-05: Behind proxy extracts client IP from X-Real-IP and X-Forwarded-For")
    void behindProxy_extractsClientIp() throws Exception {
        String clientIp = "10.20.1.105";

        // Using X-Forwarded-For with multiple proxy hops
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/faq")
                            .header("X-Forwarded-For", clientIp + ", 10.0.0.1, 10.0.0.2"))
                    .andExpect(status().isOk());
        }

        // 6th request with same client IP in X-Forwarded-For must be blocked
        mockMvc.perform(get("/faq")
                        .header("X-Forwarded-For", clientIp + ", 10.0.0.1, 10.0.0.2"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("SEC-RL-06: Multi-tenant IP bucket isolation guarantees independent client quotas")
    void ipIsolation_independentQuotas() throws Exception {
        String ip1 = "10.20.1.106";
        String ip2 = "10.20.1.107";

        // Exhaust quota for ip1
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/faq")
                            .header("X-Real-IP", ip1))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/faq")
                        .header("X-Real-IP", ip1))
                .andExpect(status().isTooManyRequests());

        // ip2 must still have its full quota available
        mockMvc.perform(get("/faq")
                        .header("X-Real-IP", ip2))
                .andExpect(status().isOk());
    }
}
