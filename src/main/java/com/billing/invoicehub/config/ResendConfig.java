package com.billing.invoicehub.config;

import com.resend.Resend;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResendConfig {

    @Value("${resend.api.key}")
    private String apiKey;

    @Bean
    public Resend resend() {
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.startsWith("re_1234")) {
            // Provide a dummy/noop client or log a warning if API key is not configured/placeholder
            // In a real environment, we instantiate with the provided key.
            return new Resend(apiKey);
        }
        return new Resend(apiKey);
    }
}
