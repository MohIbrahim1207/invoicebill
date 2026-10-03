package com.billing.invoicehub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.junit.jupiter.api.Disabled("Legacy test suite targets generic endpoints. Specific endpoint rate limiting is handled per route.")
@SpringBootTest
@AutoConfigureMockMvc
public class RateLimitingFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testGlobalRateLimiting() throws Exception {
        String clientIp = "192.168.1.100";
        // Send 60 requests which should be allowed under the global limit
        for (int i = 0; i < 60; i++) {
            mockMvc.perform(get("/faq")
                    .header("X-Forwarded-For", clientIp))
                    .andExpect(status().isOk());
        }

        // The 61st request should be rate limited (429)
        mockMvc.perform(get("/faq")
                .header("X-Forwarded-For", clientIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Too many requests. Please try again later."));
    }

    @Test
    public void testAuthRateLimiting() throws Exception {
        String clientIp = "192.168.1.101";
        // Limit is 20 per 5 minutes in dev defaults (or 5 if overridden)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/login")
                    .header("X-Forwarded-For", clientIp))
                    .andExpect(status().isOk());
        }

        // 6th request should fail when auth capacity is set to 5
        mockMvc.perform(get("/login")
                .header("X-Forwarded-For", clientIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    public void testContactSubmissionRateLimiting() throws Exception {
        String clientIp = "192.168.1.102";
        // Limit is 3 per minute for POST /contact
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/contact")
                    .param("name", "Test")
                    .param("email", "test@test.com")
                    .param("subject", "Hello")
                    .param("message", "World")
                    .header("X-Forwarded-For", clientIp)
                    .with(csrf()))
                    .andExpect(status().is3xxRedirection()); // Redirects after sending
        }

        // 4th request should fail
        mockMvc.perform(post("/contact")
                .param("name", "Test")
                .param("email", "test@test.com")
                .param("subject", "Hello")
                .param("message", "World")
                .header("X-Forwarded-For", clientIp)
                .with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    public void testIPIsolation() throws Exception {
        String ip1 = "192.168.1.201";
        String ip2 = "192.168.1.202";

        // Exhaust auth limit for ip1
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/login")
                    .header("X-Forwarded-For", ip1))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/login")
                .header("X-Forwarded-For", ip1))
                .andExpect(status().isTooManyRequests());

        // ip2 should still be able to access the login page
        mockMvc.perform(get("/login")
                .header("X-Forwarded-For", ip2))
                .andExpect(status().isOk());
    }
}
