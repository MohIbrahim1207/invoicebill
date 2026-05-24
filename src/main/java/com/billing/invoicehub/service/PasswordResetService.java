/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.entity.AppUser
 *  com.billing.invoicehub.entity.PasswordResetToken
 *  com.billing.invoicehub.repository.AppUserRepository
 *  com.billing.invoicehub.repository.PasswordResetTokenRepository
 *  com.billing.invoicehub.service.PasswordResetService
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.annotation.Value
 *  org.springframework.mail.SimpleMailMessage
 *  org.springframework.mail.javamail.JavaMailSender
 *  org.springframework.security.crypto.password.PasswordEncoder
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 */
package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.PasswordResetToken;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.PasswordResetTokenRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private final PasswordResetTokenRepository tokenRepository;
    private final AppUserRepository userRepository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final String mailFrom;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository, AppUserRepository userRepository, JavaMailSender mailSender, PasswordEncoder passwordEncoder, @Value(value="${spring.mail.username:}") String mailFrom) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
        this.mailFrom = mailFrom;
    }

    @Transactional
    public String sendOtpToEmail(String username, String email) {
        Optional<AppUser> userOpt = this.userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return "USERNAME_NOT_FOUND";
        }
        AppUser user = userOpt.get();
        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(email)) {
            return "EMAIL_MISMATCH";
        }
        String otp = this.generateOtp();
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(10L);
        PasswordResetToken token = new PasswordResetToken(email, otp, expiryTime);
        this.tokenRepository.save(token);
        try {
            this.sendOtpEmail(email, otp);
            return "OK";
        }
        catch (Exception ex) {
            log.error("Failed to send OTP email to {}: {}", new Object[]{email, ex.getMessage(), ex});
            return "ERROR";
        }
    }

    @Transactional
    public boolean verifyOtp(String email, String otp) {
        Optional<PasswordResetToken> token = this.tokenRepository.findFirstByEmailAndUsedFalseOrderByCreatedAtDesc(email);
        if (token.isEmpty()) {
            return false;
        }
        PasswordResetToken resetToken = token.get();
        if (resetToken.isExpired()) {
            return false;
        }
        if (!resetToken.getOtp().equals(otp)) {
            return false;
        }
        resetToken.setUsed(true);
        this.tokenRepository.save(resetToken);
        return true;
    }

    @Transactional
    public boolean resetPassword(String email, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            return false;
        }
        Optional<AppUser> user = this.userRepository.findAll().stream().filter(u -> email.equalsIgnoreCase(u.getEmail())).findFirst();
        if (user.isEmpty()) {
            return false;
        }
        user.get().setPassword(this.passwordEncoder.encode((CharSequence)newPassword));
        this.userRepository.save(user.get());
        return true;
    }

    private String generateOtp() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    private void sendOtpEmail(String email, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("InvoiceHub - Password Reset OTP");
        message.setText("Your OTP for password reset is: " + otp + "\n\nThis OTP is valid for 10 minutes.\nIf you did not request this, ignore this email.");
        if (this.mailFrom != null && !this.mailFrom.isBlank()) {
            message.setFrom(this.mailFrom);
        }
        try {
            this.mailSender.send(message);
            log.info("Sent OTP email to {}", (Object)email);
        }
        catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", new Object[]{email, e.getMessage(), e});
            throw e;
        }
    }
}

