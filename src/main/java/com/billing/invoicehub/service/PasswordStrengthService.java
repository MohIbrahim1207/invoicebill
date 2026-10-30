package com.billing.invoicehub.service;

import com.billing.invoicehub.exception.InvalidPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class PasswordStrengthService {

    private static final Logger log = LoggerFactory.getLogger(PasswordStrengthService.class);

    private static final int MIN_LENGTH = 12;
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("[^A-Za-z0-9]");

    @Value("${app.security.hibp.enabled:true}")
    private boolean hibpEnabled;

    @Value("${app.security.hibp.timeout-seconds:3}")
    private int hibpTimeoutSeconds;

    private final MessageSource messageSource;
    private final Set<String> commonPasswords = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, Boolean> hibpCache = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public PasswordStrengthService(MessageSource messageSource) {
        this.messageSource = messageSource;
        loadCommonPasswords();
    }

    private void loadCommonPasswords() {
        try {
            ClassPathResource resource = new ClassPathResource("security/common-passwords.txt");
            if (resource.exists()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim().toLowerCase();
                        if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                            commonPasswords.add(trimmed);
                        }
                    }
                }
                log.info("Loaded {} common passwords into blacklist cache.", commonPasswords.size());
            }
        } catch (Exception e) {
            log.warn("Could not load security/common-passwords.txt: {}. Using internal fallback list.", e.getMessage());
        }

        // Built-in fallback common passwords
        List<String> fallbacks = List.of(
                "password", "123456", "12345678", "123456789", "qwerty", "admin123", "vendor123",
                "admin", "welcome", "password123", "password1234", "password123!", "pass@word1",
                "admin@123", "admin12345!", "welcome123", "changeit", "changeme", "iloveyou",
                "letmein", "monkey", "dragon", "sunshine", "princess", "football", "baseball"
        );
        for (String fb : fallbacks) {
            commonPasswords.add(fb.toLowerCase());
        }
    }

    /**
     * Validates a password against all security requirements.
     * Throws InvalidPasswordException if any requirement fails.
     */
    public void validatePassword(String password, String username, String email) {
        List<String> errorCodes = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();
        Locale locale = LocaleContextHolder.getLocale();

        if (password == null || password.length() < MIN_LENGTH) {
            errorCodes.add("validation.password.length");
            errorMessages.add(getMessage("validation.password.length", "Password must be at least 12 characters long.", locale));
        }

        if (password == null || !UPPERCASE_PATTERN.matcher(password).find()) {
            errorCodes.add("validation.password.uppercase");
            errorMessages.add(getMessage("validation.password.uppercase", "Password must contain at least one uppercase letter.", locale));
        }

        if (password == null || !LOWERCASE_PATTERN.matcher(password).find()) {
            errorCodes.add("validation.password.lowercase");
            errorMessages.add(getMessage("validation.password.lowercase", "Password must contain at least one lowercase letter.", locale));
        }

        if (password == null || !DIGIT_PATTERN.matcher(password).find()) {
            errorCodes.add("validation.password.digit");
            errorMessages.add(getMessage("validation.password.digit", "Password must contain at least one number.", locale));
        }

        if (password == null || !SYMBOL_PATTERN.matcher(password).find()) {
            errorCodes.add("validation.password.symbol");
            errorMessages.add(getMessage("validation.password.symbol", "Password must contain at least one symbol or special character.", locale));
        }

        // Check if password contains username or email
        if (password != null && containsUserIdentifier(password, username, email)) {
            errorCodes.add("validation.password.contains_user");
            errorMessages.add(getMessage("validation.password.contains_user", "Password cannot contain your username or email.", locale));
        }

        // Check against common passwords blacklist
        if (password != null && isCommonPassword(password)) {
            errorCodes.add("validation.password.common");
            errorMessages.add(getMessage("validation.password.common", "This password is too common or easily guessable. Please choose a more unique password.", locale));
        }

        // Check Have I Been Pwned API (k-anonymity, fail open on timeout/error)
        if (password != null && errorCodes.isEmpty()) {
            if (isPwned(password)) {
                errorCodes.add("validation.password.breached");
                errorMessages.add(getMessage("validation.password.breached", "This password has appeared in known data breaches (Have I Been Pwned). For your security, please choose a different password.", locale));
            }
        }

        if (!errorCodes.isEmpty()) {
            String combinedMessage = String.join(" ", errorMessages);
            throw new InvalidPasswordException(combinedMessage, errorCodes, errorMessages);
        }
    }

    public boolean isCommonPassword(String password) {
        if (password == null) return false;
        String normalized = password.trim().toLowerCase();
        return commonPasswords.contains(normalized);
    }

    public boolean containsUserIdentifier(String password, String username, String email) {
        if (password == null) return false;
        String passLower = password.toLowerCase();

        if (username != null && username.trim().length() >= 3) {
            String cleanUser = username.trim().toLowerCase();
            if (passLower.contains(cleanUser)) {
                return true;
            }
        }

        if (email != null && email.trim().length() >= 3) {
            String cleanEmail = email.trim().toLowerCase();
            if (passLower.contains(cleanEmail)) {
                return true;
            }
            int atIndex = cleanEmail.indexOf('@');
            if (atIndex >= 3) {
                String localPart = cleanEmail.substring(0, atIndex);
                if (passLower.contains(localPart)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks if password has appeared in data breaches using HIBP Pwned Passwords k-anonymity API.
     * Transmits ONLY the first 5 characters of SHA-1 hash (never the full password).
     * Fails open (returns false with warning in log) if API times out or is unreachable.
     */
    public boolean isPwned(String password) {
        if (!hibpEnabled || password == null || password.isEmpty()) {
            return false;
        }

        try {
            String sha1Hex = sha1Hex(password);
            String prefix = sha1Hex.substring(0, 5);
            String suffix = sha1Hex.substring(5);

            // Check in-memory cache first to avoid duplicate network latency
            if (hibpCache.containsKey(sha1Hex)) {
                return hibpCache.get(sha1Hex);
            }

            URI uri = URI.create("https://api.pwnedpasswords.com/range/" + prefix);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(Math.max(1, hibpTimeoutSeconds)))
                    .header("User-Agent", "InvoiceHub-PasswordSecurityCheck/1.0")
                    .header("Add-Padding", "true") // HIBP privacy enhancement
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("HIBP Pwned Passwords API returned status {}. Failing open to prevent blocking user.", response.statusCode());
                return false;
            }

            String body = response.body();
            boolean breached = false;
            if (body != null) {
                String[] lines = body.split("\\r?\\n");
                for (String line : lines) {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        String hashSuffix = line.substring(0, colon).trim();
                        if (hashSuffix.equalsIgnoreCase(suffix)) {
                            breached = true;
                            break;
                        }
                    }
                }
            }

            // Keep cache bounded
            if (hibpCache.size() > 5000) {
                hibpCache.clear();
            }
            hibpCache.put(sha1Hex, breached);

            if (breached) {
                log.info("Password hash matched HIBP breached database.");
            }
            return breached;
        } catch (Exception e) {
            log.warn("HIBP API check failed or timed out: {}. Failing open (allowing password).", e.getMessage());
            return false;
        }
    }

    private String sha1Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private String getMessage(String code, String defaultMessage, Locale locale) {
        try {
            return messageSource.getMessage(code, null, defaultMessage, locale);
        } catch (Exception e) {
            return defaultMessage;
        }
    }
}
