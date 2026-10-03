-- SEC-004: Add OTP attempt counter to password_reset_token table.
-- After MAX_OTP_ATTEMPTS (5) failed verifications, the token is permanently invalidated.
ALTER TABLE password_reset_token
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0
        COMMENT 'Number of failed OTP verification attempts. Token is locked after 5 failures.';
