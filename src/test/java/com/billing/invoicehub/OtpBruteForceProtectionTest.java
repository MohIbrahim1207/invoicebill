package com.billing.invoicehub;

import com.billing.invoicehub.entity.PasswordResetToken;
import com.billing.invoicehub.repository.PasswordResetTokenRepository;
import com.billing.invoicehub.service.PasswordResetService;
import com.billing.invoicehub.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-004: OTP brute-force protection integration tests.
 *
 * Checklist verified:
 * 1.  Token starts with attemptCount = 0.
 * 2.  Attempts 1–4: each returns INVALID and increments the counter.
 * 3.  Attempt 5 (wrong): returns LOCKED, token is invalidated.
 * 4.  A 6th incorrect OTP after lock-out returns LOCKED (not NOT_FOUND).
 * 5.  A correct OTP after lock-out returns LOCKED (not SUCCESS, not NOT_FOUND).
 * 6.  A correct OTP marks the token as used; replaying the same OTP returns NOT_FOUND.
 * 7.  An expired token returns EXPIRED.
 * 8.  No token ever issued returns NOT_FOUND.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.profiles.active=dev",
        "app.seed.enabled=false",
        "rate-limiting.enabled=false"
})
@Transactional
class OtpBruteForceProtectionTest {

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @MockBean
    private EmailService emailService; // Prevent real emails during tests

    private static final String TEST_EMAIL = "test-otp@example.com";
    private static final String CORRECT_OTP = "123456";

    private PasswordResetToken savedToken;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        PasswordResetToken token = new PasswordResetToken(
                TEST_EMAIL,
                CORRECT_OTP,
                LocalDateTime.now().plusMinutes(10)
        );
        // Checklist #1: token starts with attemptCount = 0
        assertThat(token.getAttemptCount()).isZero();
        assertThat(token.isUsed()).isFalse();
        savedToken = tokenRepository.save(token);
    }

    // ─── Checklist #6 ──────────────────────────────────────────────────────────

    @Test
    void correctOtp_shouldReturnSuccess_andMarkTokenUsed() {
        var result = passwordResetService.verifyOtp(TEST_EMAIL, CORRECT_OTP);

        assertThat(result).isEqualTo(PasswordResetService.OtpVerificationResult.SUCCESS);

        PasswordResetToken token = tokenRepository.findById(savedToken.getId()).orElseThrow();
        assertThat(token.isUsed()).isTrue();
        assertThat(token.getAttemptCount()).isZero(); // success path must NOT increment attempts
    }

    @Test
    void successfulOtp_cannotBeReplayed_returnsNotFound() {
        // First call succeeds
        passwordResetService.verifyOtp(TEST_EMAIL, CORRECT_OTP);

        // Replay with the same OTP — token is now used=true with attemptCount=0,
        // so the fallback query sees used=true and attemptCount < MAX_ATTEMPTS → NOT_FOUND.
        var replay = passwordResetService.verifyOtp(TEST_EMAIL, CORRECT_OTP);
        assertThat(replay).isEqualTo(PasswordResetService.OtpVerificationResult.NOT_FOUND);
    }

    // ─── Checklist #2 ──────────────────────────────────────────────────────────

    @Test
    void attempt1_wrongOtp_returnsInvalid_andIncrementsCount() {
        var result = passwordResetService.verifyOtp(TEST_EMAIL, "000000");

        assertThat(result).isEqualTo(PasswordResetService.OtpVerificationResult.INVALID);

        PasswordResetToken token = tokenRepository.findById(savedToken.getId()).orElseThrow();
        assertThat(token.getAttemptCount()).isEqualTo(1);
        assertThat(token.isUsed()).isFalse();
    }

    @Test
    void attempts1to4_returnInvalid_notLocked() {
        // Attempts 1–4 must each return INVALID, not LOCKED
        for (int i = 1; i <= PasswordResetToken.MAX_ATTEMPTS - 1; i++) {
            var result = passwordResetService.verifyOtp(TEST_EMAIL, "000000");
            assertThat(result)
                    .as("Attempt %d should be INVALID", i)
                    .isEqualTo(PasswordResetService.OtpVerificationResult.INVALID);
        }

        PasswordResetToken token = tokenRepository.findById(savedToken.getId()).orElseThrow();
        assertThat(token.getAttemptCount()).isEqualTo(PasswordResetToken.MAX_ATTEMPTS - 1);
        assertThat(token.isUsed()).isFalse(); // still open after 4 failures
    }

    // ─── Checklist #3 ──────────────────────────────────────────────────────────

    @Test
    void attempt5_wrongOtp_locksToken_returnsLocked() {
        // Exhaust all 5 attempts
        PasswordResetService.OtpVerificationResult lastResult = null;
        for (int i = 0; i < PasswordResetToken.MAX_ATTEMPTS; i++) {
            lastResult = passwordResetService.verifyOtp(TEST_EMAIL, "000000");
        }

        // The 5th attempt must return LOCKED
        assertThat(lastResult).isEqualTo(PasswordResetService.OtpVerificationResult.LOCKED);

        PasswordResetToken token = tokenRepository.findById(savedToken.getId()).orElseThrow();
        assertThat(token.getAttemptCount()).isEqualTo(PasswordResetToken.MAX_ATTEMPTS);
        assertThat(token.isUsed()).isTrue();  // brute-force locked sets used=true
        assertThat(token.isLocked()).isTrue();
    }

    // ─── Checklist #4 ──────────────────────────────────────────────────────────

    @Test
    void attempt6_wrongOtp_afterLockOut_returnsLocked_notNotFound() {
        // Lock the token with 5 failures
        for (int i = 0; i < PasswordResetToken.MAX_ATTEMPTS; i++) {
            passwordResetService.verifyOtp(TEST_EMAIL, "000000");
        }

        // 6th wrong OTP — must return LOCKED, NOT NOT_FOUND
        var result = passwordResetService.verifyOtp(TEST_EMAIL, "999999");
        assertThat(result)
                .as("After lock-out, a 6th wrong OTP must return LOCKED not NOT_FOUND")
                .isEqualTo(PasswordResetService.OtpVerificationResult.LOCKED);
    }

    // ─── Checklist #5 ──────────────────────────────────────────────────────────

    @Test
    void correctOtp_afterLockOut_returnsLocked_notSuccess() {
        // Lock the token
        for (int i = 0; i < PasswordResetToken.MAX_ATTEMPTS; i++) {
            passwordResetService.verifyOtp(TEST_EMAIL, "000000");
        }

        // Correct OTP after lock-out must return LOCKED, not SUCCESS and not NOT_FOUND
        var result = passwordResetService.verifyOtp(TEST_EMAIL, CORRECT_OTP);
        assertThat(result)
                .as("A correct OTP after brute-force lock-out must return LOCKED")
                .isEqualTo(PasswordResetService.OtpVerificationResult.LOCKED);
    }

    // ─── Checklist #7 ──────────────────────────────────────────────────────────

    @Test
    void expiredToken_shouldReturnExpired() {
        savedToken.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        tokenRepository.save(savedToken);

        var result = passwordResetService.verifyOtp(TEST_EMAIL, CORRECT_OTP);
        assertThat(result).isEqualTo(PasswordResetService.OtpVerificationResult.EXPIRED);
    }

    // ─── Checklist #8 ──────────────────────────────────────────────────────────

    @Test
    void noToken_shouldReturnNotFound() {
        var result = passwordResetService.verifyOtp("nobody@example.com", CORRECT_OTP);
        assertThat(result).isEqualTo(PasswordResetService.OtpVerificationResult.NOT_FOUND);
    }
}
