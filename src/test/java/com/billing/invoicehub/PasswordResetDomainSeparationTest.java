package com.billing.invoicehub;

import com.billing.invoicehub.entity.AppRole;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.PasswordResetToken;
import com.billing.invoicehub.repository.AppRoleRepository;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.PasswordResetTokenRepository;
import com.billing.invoicehub.service.EmailService;
import com.billing.invoicehub.service.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.profiles.active=dev",
        "app.seed.enabled=false",
        "rate-limiting.enabled=false"
})
@Transactional
public class PasswordResetDomainSeparationTest {

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AppRoleRepository roleRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private EmailService emailService;

    private static final String SHARED_EMAIL = "shared.reset@example.com";
    private static final String VENDOR_USER = "vendor_reset_user";
    private static final String ADMIN_USER = "admin_reset_user";
    private static final String INITIAL_PASSWORD = "InitialPassword123!";
    private static final String NEW_PASSWORD = "NewPassword1234!";

    @BeforeEach
    void setupDualDomainUsers() {
        tokenRepository.deleteAll();

        AppRole vendorRole = roleRepository.findFirstByName("ROLE_VENDOR").orElseGet(() -> {
            AppRole r = new AppRole();
            r.setName("ROLE_VENDOR");
            return roleRepository.save(r);
        });

        AppRole adminRole = roleRepository.findFirstByName("ROLE_ADMIN").orElseGet(() -> {
            AppRole r = new AppRole();
            r.setName("ROLE_ADMIN");
            return roleRepository.save(r);
        });

        // 1. Create vendor account with shared email
        AppUser vendor = new AppUser();
        vendor.setUsername(VENDOR_USER);
        vendor.setEmail(SHARED_EMAIL);
        vendor.setUserDomain("VENDOR");
        vendor.setPassword(passwordEncoder.encode(INITIAL_PASSWORD));
        vendor.setEnabled(true);
        vendor.setVerified(true);
        vendor.getRoles().add(vendorRole);
        userRepository.save(vendor);

        // 2. Create internal/admin account with identical shared email
        AppUser admin = new AppUser();
        admin.setUsername(ADMIN_USER);
        admin.setEmail(SHARED_EMAIL);
        admin.setUserDomain("INTERNAL");
        admin.setPassword(passwordEncoder.encode(INITIAL_PASSWORD));
        admin.setEnabled(true);
        admin.setVerified(true);
        admin.getRoles().add(adminRole);
        userRepository.save(admin);
    }

    @Test
    @DisplayName("Password Reset: Targeted user password is reset while other domain account with same email remains intact")
    void resetPasswordWithUsername_shouldOnlyAffectTargetDomainUser() {
        // Request OTP specifically for vendor account
        String otpResult = passwordResetService.sendOtpToEmail(VENDOR_USER, SHARED_EMAIL);
        assertEquals("OK", otpResult, "OTP generation must succeed for valid (username, email) pair");

        // Verify OTP was stored in token repository
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findFirstByEmailAndUsedFalseOrderByCreatedAtDesc(SHARED_EMAIL);
        assertTrue(tokenOpt.isPresent(), "Token must be generated and stored for the email");
        String otp = tokenOpt.get().getOtp();

        // Verify the OTP
        PasswordResetService.OtpVerificationResult verifyResult = passwordResetService.verifyOtp(SHARED_EMAIL, otp);
        assertEquals(PasswordResetService.OtpVerificationResult.SUCCESS, verifyResult);

        // Reset password specifying vendor username
        boolean resetSuccess = passwordResetService.resetPassword(VENDOR_USER, SHARED_EMAIL, NEW_PASSWORD, NEW_PASSWORD);
        assertTrue(resetSuccess, "Password reset must succeed when username and email are provided");

        // Verify vendor password has changed
        AppUser updatedVendor = userRepository.findFirstByUsernameAndUserDomain(VENDOR_USER, "VENDOR").orElseThrow();
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, updatedVendor.getPassword()), "Vendor password must be updated to new password");

        // CRITICAL CHECK: Verify admin account password was NOT altered
        AppUser unchangedAdmin = userRepository.findFirstByUsernameAndUserDomain(ADMIN_USER, "INTERNAL").orElseThrow();
        assertTrue(passwordEncoder.matches(INITIAL_PASSWORD, unchangedAdmin.getPassword()), "Admin password must remain completely unchanged");
        assertFalse(passwordEncoder.matches(NEW_PASSWORD, unchangedAdmin.getPassword()), "Admin password must not have been overwritten");
    }

    @Test
    @DisplayName("Password Reset: Ambiguous reset without username is blocked when duplicate emails exist across domains")
    void resetPasswordWithoutUsername_shouldFailWhenMultipleAccountsShareEmail() {
        // When multiple accounts exist with same email, reset without username must return false to prevent corrupting wrong account
        boolean result = passwordResetService.resetPassword(null, SHARED_EMAIL, NEW_PASSWORD, NEW_PASSWORD);
        assertFalse(result, "Reset must be rejected when multiple accounts share an email and no username is specified");

        // Verify neither account was modified
        AppUser vendor = userRepository.findFirstByUsernameAndUserDomain(VENDOR_USER, "VENDOR").orElseThrow();
        AppUser admin = userRepository.findFirstByUsernameAndUserDomain(ADMIN_USER, "INTERNAL").orElseThrow();
        assertTrue(passwordEncoder.matches(INITIAL_PASSWORD, vendor.getPassword()));
        assertTrue(passwordEncoder.matches(INITIAL_PASSWORD, admin.getPassword()));
    }

    @Test
    @DisplayName("Password Reset: Single account email without username resets correctly")
    void resetPasswordWithoutUsername_singleAccountEmail_shouldSucceed() {
        String uniqueEmail = "unique.single@example.com";
        String uniqueUser = "unique_single_user";

        AppRole vendorRole = roleRepository.findFirstByName("ROLE_VENDOR").orElseThrow();
        AppUser singleUser = new AppUser();
        singleUser.setUsername(uniqueUser);
        singleUser.setEmail(uniqueEmail);
        singleUser.setUserDomain("VENDOR");
        singleUser.setPassword(passwordEncoder.encode(INITIAL_PASSWORD));
        singleUser.setEnabled(true);
        singleUser.setVerified(true);
        singleUser.getRoles().add(vendorRole);
        userRepository.save(singleUser);

        boolean result = passwordResetService.resetPassword(null, uniqueEmail, NEW_PASSWORD, NEW_PASSWORD);
        assertTrue(result, "Reset without username should succeed if email belongs to exactly one account");

        AppUser updated = userRepository.findFirstByUsernameAndUserDomain(uniqueUser, "VENDOR").orElseThrow();
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, updated.getPassword()));
    }

    @Test
    @DisplayName("Security: Mismatched username and email returns generic USER_NOT_FOUND without revealing details")
    void sendOtp_mismatchedUsernameAndEmail_returnsUserNotFound() {
        // Valid username with wrong email
        String resultWrongEmail = passwordResetService.sendOtpToEmail(VENDOR_USER, "wrong@example.com");
        assertEquals("USER_NOT_FOUND", resultWrongEmail);

        // Nonexistent username with valid email
        String resultWrongUser = passwordResetService.sendOtpToEmail("nonexistent_user", SHARED_EMAIL);
        assertEquals("USER_NOT_FOUND", resultWrongUser);

        // Blank inputs
        assertEquals("USER_NOT_FOUND", passwordResetService.sendOtpToEmail("", SHARED_EMAIL));
        assertEquals("USER_NOT_FOUND", passwordResetService.sendOtpToEmail(VENDOR_USER, ""));
    }

    @Test
    @DisplayName("Security: Password policy rejects short passwords and passwords missing numbers")
    void resetPassword_enforcesPasswordPolicy() {
        // Less than 8 chars
        assertFalse(passwordResetService.resetPassword(VENDOR_USER, SHARED_EMAIL, "Short1", "Short1"));

        // No digit
        assertFalse(passwordResetService.resetPassword(VENDOR_USER, SHARED_EMAIL, "NoDigitsHere!", "NoDigitsHere!"));

        // Passwords do not match
        assertFalse(passwordResetService.resetPassword(VENDOR_USER, SHARED_EMAIL, NEW_PASSWORD, "DifferentPassword123!"));
    }
}
