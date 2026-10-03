package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.PasswordResetToken;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.PasswordResetTokenRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final PasswordResetTokenRepository tokenRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                AppUserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Transactional
    public String sendOtpToEmail(String username, String email) {
        Optional<AppUser> userOpt = this.userRepository.findByUsername(username);

        // Generic check — never reveal whether username or email exists
        if (userOpt.isEmpty() || userOpt.get().getEmail() == null
                || !userOpt.get().getEmail().equalsIgnoreCase(email)) {
            return "USER_NOT_FOUND";
        }

        AppUser user = userOpt.get();
        String otp = this.generateOtp();
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(10L);
        PasswordResetToken token = new PasswordResetToken(email, otp, expiryTime);
        this.tokenRepository.save(token);

        try {
            this.sendOtpEmail(email, otp);
            return "OK";
        } catch (Exception ex) {
            log.error("Failed to send OTP email to {}: {}", email, ex.getMessage(), ex);
            return "ERROR";
        }
    }

    /** Result codes for OTP verification — avoids ambiguity between wrong OTP and locked token. */
    public enum OtpVerificationResult {
        SUCCESS, INVALID, LOCKED, EXPIRED, NOT_FOUND
    }

    @Transactional
    public OtpVerificationResult verifyOtp(String email, String otp) {
        // First: look for an unused (open) token.
        Optional<PasswordResetToken> unusedToken =
                this.tokenRepository.findFirstByEmailAndUsedFalseOrderByCreatedAtDesc(email);

        if (unusedToken.isPresent()) {
            PasswordResetToken resetToken = unusedToken.get();

            // SEC-004: Reject if already at the attempt limit (defensive — should not normally occur
            // since the 5th failure sets used=true, but guards against any concurrent edge case).
            if (resetToken.getAttemptCount() >= PasswordResetToken.MAX_ATTEMPTS) {
                resetToken.setUsed(true);
                this.tokenRepository.save(resetToken);
                log.warn("OTP token locked for '{}': attempt_count={} with used=false (corrected)", email, resetToken.getAttemptCount());
                return OtpVerificationResult.LOCKED;
            }

            if (resetToken.isExpired()) return OtpVerificationResult.EXPIRED;

            if (!resetToken.getOtp().equals(otp)) {
                // SEC-004: Increment failure counter; lock token if limit reached.
                int attempts = resetToken.incrementAttempts();
                if (attempts >= PasswordResetToken.MAX_ATTEMPTS) {
                    resetToken.setUsed(true);  // Brute-force locked — mark used to invalidate.
                    log.warn("OTP token brute-force locked for '{}' after {} failed attempts", email, attempts);
                    this.tokenRepository.save(resetToken);
                    return OtpVerificationResult.LOCKED;
                }
                this.tokenRepository.save(resetToken);
                return OtpVerificationResult.INVALID;
            }

            // Correct OTP — mark as used to prevent replay.
            resetToken.setUsed(true);
            this.tokenRepository.save(resetToken);
            return OtpVerificationResult.SUCCESS;
        }

        // No unused token found. Check whether a locked token exists for this email
        // so we can return LOCKED instead of NOT_FOUND when the user was brute-force locked.
        Optional<PasswordResetToken> latestToken =
                this.tokenRepository.findFirstByEmailOrderByCreatedAtDesc(email);

        if (latestToken.isPresent()) {
            PasswordResetToken t = latestToken.get();
            // used=true AND attempt_count >= MAX_ATTEMPTS → brute-force locked
            if (t.getAttemptCount() >= PasswordResetToken.MAX_ATTEMPTS) {
                log.warn("OTP verification rejected for '{}': token is brute-force locked (attempts={})", email, t.getAttemptCount());
                return OtpVerificationResult.LOCKED;
            }
            // used=true with attemptCount < MAX_ATTEMPTS → legitimately consumed (successful reset)
            // Fall through to NOT_FOUND so the user is prompted to request a new OTP.
        }

        return OtpVerificationResult.NOT_FOUND;
    }

    @Transactional
    public boolean resetPassword(String email, String newPassword, String confirmPassword) {
        // Password policy: min 8 chars, at least one number
        if (newPassword.length() < 8 || !newPassword.matches(".*\\d.*")) {
            return false;
        }

        if (!newPassword.equals(confirmPassword)) return false;

        // Use findByEmailIgnoreCase instead of findAll()
        Optional<AppUser> user = this.userRepository.findByEmailIgnoreCase(email);
        if (user.isEmpty()) return false;

        user.get().setPassword(this.passwordEncoder.encode(newPassword));
        this.userRepository.save(user.get());
        return true;
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        return String.valueOf(100000 + random.nextInt(900000));
    }

    private void sendOtpEmail(String email, String otp) {
        try {
            this.emailService.sendPasswordResetOtpEmail(email, otp);
            log.info("Sent OTP email to {}", email);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", email, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}