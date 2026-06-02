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

    @Transactional
    public boolean verifyOtp(String email, String otp) {
        Optional<PasswordResetToken> token =
                this.tokenRepository.findFirstByEmailAndUsedFalseOrderByCreatedAtDesc(email);
        if (token.isEmpty()) return false;

        PasswordResetToken resetToken = token.get();
        if (resetToken.isExpired()) return false;
        if (!resetToken.getOtp().equals(otp)) return false;

        resetToken.setUsed(true);
        this.tokenRepository.save(resetToken);
        return true;
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