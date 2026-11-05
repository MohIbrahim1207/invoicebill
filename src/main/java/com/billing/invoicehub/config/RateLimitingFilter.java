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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final RateLimitConfig config;

    // Caches to hold per-IP buckets, evicting entries not accessed in 10 minutes
    private final Cache<String, Bucket> authCache;
    private final Cache<String, Bucket> registerCache;
    private final Cache<String, Bucket> uploadCache;
    private final Cache<String, Bucket> contactCache;
    private final Cache<String, Bucket> devMailCache;

    public RateLimitingFilter(RateLimitConfig config) {
        this.config = config;

        this.authCache = Caffeine.newBuilder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build();
        this.registerCache = Caffeine.newBuilder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build();
        this.uploadCache = Caffeine.newBuilder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build();
        this.contactCache = Caffeine.newBuilder()
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

        String path = request.getRequestURI();
        if (isExcludedPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String method = request.getMethod();
        String ip = getClientIp(request);

        // Rate-limit ONLY login, signup, OTP, forgot-password, and uploads
        // Page loads, static files, /ws, notifications, and dashboard are explicitly NOT rate-limited.
        if (isAuthEndpoint(method, path)) {
            Bucket bucket = getOrCreateBucket(ip, authCache, config.getAuthCapacity(),
                    config.getAuthRefillTokens(), config.getAuthRefillPeriodMinutes());
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                sendTooManyRequestsResponse(request, response, ip, path, probe.getNanosToWaitForRefill());
                return;
            }
        } else if (isVendorRegistrationEndpoint(method, path)) {
            Bucket bucket = getOrCreateBucket(ip, registerCache, config.getRegisterCapacity(),
                    config.getRegisterRefillTokens(), config.getRegisterRefillPeriodMinutes());
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                sendTooManyRequestsResponse(request, response, ip, path, probe.getNanosToWaitForRefill());
                return;
            }
        } else if (isUploadEndpoint(request, method, path)) {
            long uploadCap = config.getUploadCapacity() > 0 ? config.getUploadCapacity() : 15;
            long uploadRefill = config.getUploadRefillTokens() > 0 ? config.getUploadRefillTokens() : 15;
            long uploadPeriod = config.getUploadRefillPeriodMinutes() > 0 ? config.getUploadRefillPeriodMinutes() : 1;
            Bucket bucket = getOrCreateBucket(ip, uploadCache, uploadCap, uploadRefill, uploadPeriod);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                sendTooManyRequestsResponse(request, response, ip, path, probe.getNanosToWaitForRefill());
                return;
            }
        } else if (isContactSubmissionEndpoint(method, path)) {
            Bucket bucket = getOrCreateBucket(ip, contactCache, config.getContactCapacity(),
                    config.getContactRefillTokens(), config.getContactRefillPeriodMinutes());
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                sendTooManyRequestsResponse(request, response, ip, path, probe.getNanosToWaitForRefill());
                return;
            }
        } else if (isDevMailEndpoint(path)) {
            Bucket bucket = getOrCreateBucket(ip, devMailCache, config.getDevMailCapacity(),
                    config.getDevMailRefillTokens(), config.getDevMailRefillPeriodMinutes());
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                sendTooManyRequestsResponse(request, response, ip, path, probe.getNanosToWaitForRefill());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExcludedPath(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.startsWith("/webjars/")
                || path.startsWith("/ws/")
                || path.startsWith("/ws")
                || path.startsWith("/notifications")
                || path.startsWith("/dashboard")
                || path.equals("/favicon.ico")
                || path.startsWith("/favicon-")
                || path.equals("/site.webmanifest");
    }

    /**
     * Requirement: use X-Forwarded-For / X-Real-IP only when request comes from 127.0.0.1 (reverse proxy loopback).
     * If request comes from an external remote IP directly, ignore forwarded headers to prevent spoofing.
     */
    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        boolean isLoopback = isLoopbackAddress(remoteAddr);

        if (isLoopback && config.isBehindProxy()) {
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String[] ips = xff.split(",");
                if (ips.length > 0) {
                    String candidate = ips[0].trim();
                    if (!candidate.isBlank()) {
                        return candidate;
                    }
                }
            }
        }
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    private boolean isLoopbackAddress(String addr) {
        if (addr == null) {
            return false;
        }
        return addr.equals("127.0.0.1")
                || addr.equals("0:0:0:0:0:0:0:1")
                || addr.equals("::1")
                || addr.startsWith("127.");
    }

    private Bucket getOrCreateBucket(String ip, Cache<String, Bucket> cache, long capacity, long refillTokens,
            long periodMinutes) {
        return cache.get(ip, key -> Bucket.builder()
                .addLimit(
                        Bandwidth.classic(capacity, Refill.intervally(refillTokens, Duration.ofMinutes(periodMinutes))))
                .build());
    }

    private void sendTooManyRequestsResponse(HttpServletRequest request, HttpServletResponse response,
                                             String ip, String path, long waitForRefillNanos) throws IOException {
        long waitForRefillSeconds = (long) Math.ceil(waitForRefillNanos / 1_000_000_000.0);
        if (waitForRefillSeconds <= 0) {
            waitForRefillSeconds = 1;
        }

        // Requirement: Log blocked IP + path at WARN
        log.warn("Rate limit exceeded for IP {} on path {} (Retry-After: {}s)", ip, path, waitForRefillSeconds);

        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(waitForRefillSeconds));

        // Requirement: JSON only for /api/**; friendly bilingual page for browser pages
        if (path.startsWith("/api/")) {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"success\": false, \"error\": \"Too many requests. Please try again later.\", \"retryAfterSeconds\": "
                    + waitForRefillSeconds + "}");
            response.getWriter().flush();
            return;
        }

        response.setContentType("text/html;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        String html = """
            <!DOCTYPE html>
            <html lang="id">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>429 - Terlalu Banyak Permintaan / Too Many Requests</title>
                <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css">
                <style>
                    body { background: #f8fafc; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
                    .error-container { max-width: 560px; margin: 80px auto; }
                    .card { border-radius: 12px; border: 1px solid #e2e8f0; }
                </style>
            </head>
            <body>
                <div class="container error-container">
                    <div class="card shadow-sm p-4 text-center bg-white">
                        <div class="mb-3">
                            <span class="badge bg-warning text-dark px-3 py-2 fw-semibold">HTTP 429</span>
                        </div>
                        <h4 class="fw-bold text-dark mb-2">Terlalu Banyak Permintaan</h4>
                        <p class="text-muted mb-3">
                            Anda telah melakukan terlalu banyak permintaan dalam waktu singkat. Demi menjaga keamanan akun dan sistem, silakan tunggu sekitar <strong>%d detik</strong> sebelum mencoba kembali.
                        </p>
                        <hr class="my-3">
                        <h5 class="fw-semibold text-secondary mb-2">Too Many Requests</h5>
                        <p class="text-muted small mb-4">
                            You have made too many requests in a short period of time. To protect account security, please wait approximately <strong>%d seconds</strong> before trying again.
                        </p>
                        <div class="d-flex justify-content-center gap-2">
                            <button onclick="window.location.reload()" class="btn btn-primary btn-sm px-3">Coba Lagi / Refresh</button>
                            <a href="/" class="btn btn-outline-secondary btn-sm px-3">Beranda / Home</a>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(waitForRefillSeconds, waitForRefillSeconds);

        response.getWriter().write(html);
        response.getWriter().flush();
    }

    /**
     * Auth endpoints: Login, OTP, Forgot Password, Reset Password
     */
    private boolean isAuthEndpoint(String method, String path) {
        if (!"POST".equalsIgnoreCase(method)) {
            return false;
        }
        return path.equals("/auth/login")
                || path.equals("/login")
                || path.equals("/admin/login")
                || path.equals("/vendor/authenticate")
                || path.equals("/admin/authenticate")
                || path.equals("/forgot-password")
                || path.equals("/verify-otp")
                || path.equals("/reset-password")
                || path.equals("/change-password")
                || path.equals("/admin/change-password");
    }

    /**
     * Registration / Signup endpoints
     */
    private boolean isVendorRegistrationEndpoint(String method, String path) {
        return ("POST".equalsIgnoreCase(method) && (path.equals("/register") || path.equals("/signup")))
                || path.startsWith("/api/register");
    }

    /**
     * File upload endpoints
     */
    private boolean isUploadEndpoint(HttpServletRequest request, String method, String path) {
        if (!"POST".equalsIgnoreCase(method) && !"PUT".equalsIgnoreCase(method)) {
            return false;
        }
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/form-data")) {
            return true;
        }
        return path.equals("/saveInvoice")
                || path.equals("/updateInvoice")
                || path.startsWith("/vendor-tickets/new/step")
                || path.equals("/vendor-tickets/save")
                || path.startsWith("/admin/ai-quote-converter/upload")
                || path.startsWith("/api/upload");
    }

    private boolean isContactSubmissionEndpoint(String method, String path) {
        return "POST".equalsIgnoreCase(method) && path.equals("/contact");
    }

    private boolean isDevMailEndpoint(String path) {
        return path.startsWith("/dev/mail");
    }
}
