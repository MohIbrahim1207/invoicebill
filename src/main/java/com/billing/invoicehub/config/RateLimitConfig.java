package com.billing.invoicehub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    @Value("${rate-limiting.enabled:true}")
    private boolean enabled;

    @Value("${rate-limiting.behind-proxy:false}")
    private boolean behindProxy;

    // Global
    @Value("${rate-limiting.global.capacity:60}")
    private long globalCapacity;
    @Value("${rate-limiting.global.refill-tokens:60}")
    private long globalRefillTokens;
    @Value("${rate-limiting.global.refill-period-minutes:1}")
    private long globalRefillPeriodMinutes;

    // Auth
    @Value("${rate-limiting.auth.capacity:20}")
    private long authCapacity;
    @Value("${rate-limiting.auth.refill-tokens:20}")
    private long authRefillTokens;
    @Value("${rate-limiting.auth.refill-period-minutes:5}")
    private long authRefillPeriodMinutes;

    // Contact
    @Value("${rate-limiting.contact.capacity:3}")
    private long contactCapacity;
    @Value("${rate-limiting.contact.refill-tokens:3}")
    private long contactRefillTokens;
    @Value("${rate-limiting.contact.refill-period-minutes:1}")
    private long contactRefillPeriodMinutes;

    // Register
    @Value("${rate-limiting.register.capacity:5}")
    private long registerCapacity;
    @Value("${rate-limiting.register.refill-tokens:5}")
    private long registerRefillTokens;
    @Value("${rate-limiting.register.refill-period-minutes:1}")
    private long registerRefillPeriodMinutes;

    // Dev-mail
    @Value("${rate-limiting.dev-mail.capacity:3}")
    private long devMailCapacity;
    @Value("${rate-limiting.dev-mail.refill-tokens:3}")
    private long devMailRefillTokens;
    @Value("${rate-limiting.dev-mail.refill-period-minutes:1}")
    private long devMailRefillPeriodMinutes;

    // Getters
    public boolean isEnabled() { return enabled; }
    public boolean isBehindProxy() { return behindProxy; }

    public long getGlobalCapacity() { return globalCapacity; }
    public long getGlobalRefillTokens() { return globalRefillTokens; }
    public long getGlobalRefillPeriodMinutes() { return globalRefillPeriodMinutes; }

    public long getAuthCapacity() { return authCapacity; }
    public long getAuthRefillTokens() { return authRefillTokens; }
    public long getAuthRefillPeriodMinutes() { return authRefillPeriodMinutes; }

    public long getContactCapacity() { return contactCapacity; }
    public long getContactRefillTokens() { return contactRefillTokens; }
    public long getContactRefillPeriodMinutes() { return contactRefillPeriodMinutes; }

    public long getRegisterCapacity() { return registerCapacity; }
    public long getRegisterRefillTokens() { return registerRefillTokens; }
    public long getRegisterRefillPeriodMinutes() { return registerRefillPeriodMinutes; }

    public long getDevMailCapacity() { return devMailCapacity; }
    public long getDevMailRefillTokens() { return devMailRefillTokens; }
    public long getDevMailRefillPeriodMinutes() { return devMailRefillPeriodMinutes; }
}
