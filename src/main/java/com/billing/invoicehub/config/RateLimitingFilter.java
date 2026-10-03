package com.billing.invoicehub.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitConfig config;

    // Caches to hold per-IP buckets, evicting entries not accessed in 10 minutes
    private final Cache<String, Bucket> globalCache;
    private final Cache<String, Bucket> authCache;
    private final Cache<String, Bucket> contactCache;
    private final Cache<String, Bucket> registerCache;
    private final Cache<String, Bucket> devMailCache;

    public RateLimitingFilter(RateLimitConfig config) {
        this.config = config;

        // Initialize Caffeine caches with 10 minutes inactive eviction
        this.globalCache = Caffeine.newBuilder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build();
        this.authCache = Caffeine.newBuilder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build();
        this.contactCache = Caffeine.newBuilder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build();
        this.registerCache = Caffeine.newBuilder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build();
        this.devMailCache = Caffeine.newBuilder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!config.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Rate limiting only applies to genuinely sensitive endpoints: /login, /employee/authenticate, auth, password reset, registration
        if (isAuthEndpoint(method, path)) {
            Bucket bucket = getOrCreateBucket(ip, authCache, config.getAuthCapacity(),
                    config.getAuthRefillTokens(), config.getAuthRefillPeriodMinutes());
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                sendTooManyRequestsResponse(response, probe.getNanosToWaitForRefill());
                return;
            }
        } else if (isContactSubmissionEndpoint(method, path)) {
            Bucket bucket = getOrCreateBucket(ip, contactCache, config.getContactCapacity(),
                    config.getContactRefillTokens(), config.getContactRefillPeriodMinutes());
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                sendTooManyRequestsResponse(response, probe.getNanosToWaitForRefill());
                return;
            }
        } else if (isVendorRegistrationSubmissionEndpoint(method, path)) {
            Bucket bucket = getOrCreateBucket(ip, registerCache, config.getRegisterCapacity(),
                    config.getRegisterRefillTokens(), config.getRegisterRefillPeriodMinutes());
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                sendTooManyRequestsResponse(response, probe.getNanosToWaitForRefill());
                return;
            }
        } else if (isVendorValidationEndpoint(method, path)) {
            Bucket bucket = getOrCreateBucket(ip, globalCache, config.getGlobalCapacity(),
                    config.getGlobalRefillTokens(), config.getGlobalRefillPeriodMinutes());
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                sendTooManyRequestsResponse(response, probe.getNanosToWaitForRefill());
                return;
            }
        } else if (isDevMailEndpoint(path)) {
            Bucket bucket = getOrCreateBucket(ip, devMailCache, config.getDevMailCapacity(),
                    config.getDevMailRefillTokens(), config.getDevMailRefillPeriodMinutes());
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                sendTooManyRequestsResponse(response, probe.getNanosToWaitForRefill());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        if (config.isBehindProxy()) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String[] ips = xff.split(",");
                if (ips.length > 0) {
                    return ips[0].trim();
                }
            }
        }
        return request.getRemoteAddr();
    }

    private Bucket getOrCreateBucket(String ip, Cache<String, Bucket> cache, long capacity, long refillTokens, long periodMinutes) {
        return cache.get(ip, key -> Bucket.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.intervally(refillTokens, Duration.ofMinutes(periodMinutes))))
                .build());
    }

    private void sendTooManyRequestsResponse(HttpServletResponse response, long waitForRefillNanos) throws IOException {
        long waitForRefillSeconds = (long) Math.ceil(waitForRefillNanos / 1_000_000_000.0);
        if (waitForRefillSeconds <= 0) {
            waitForRefillSeconds = 1;
        }

        response.setStatus(429);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(waitForRefillSeconds));
        response.getWriter().write("{\"success\": false, \"error\": \"Too many requests. Please try again later.\"}");
        response.getWriter().flush();
    }

    private boolean isAuthEndpoint(String method, String path) {
        if (!"POST".equalsIgnoreCase(method)) {
            return false;
        }
        return path.equals("/auth/login")
                || path.equals("/login")
                || path.equals("/employee/login")
                || path.equals("/admin/login")
                || path.equals("/vendor/authenticate")
                || path.equals("/admin/authenticate")
                || path.equals("/employee/authenticate")
                || path.equals("/forgot-password")
                || path.equals("/verify-otp")
                || path.equals("/reset-password");
    }

    private boolean isContactSubmissionEndpoint(String method, String path) {
        return "POST".equalsIgnoreCase(method) && path.equals("/contact");
    }

    private boolean isVendorRegistrationSubmissionEndpoint(String method, String path) {
        return "POST".equalsIgnoreCase(method) && path.equals("/register");
    }

    private boolean isVendorValidationEndpoint(String method, String path) {
        return "GET".equalsIgnoreCase(method) && path.startsWith("/api/register/");
    }

    private boolean isDevMailEndpoint(String path) {
        return path.startsWith("/dev/mail");
    }
}
