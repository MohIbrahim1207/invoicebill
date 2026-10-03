/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.entity.PasswordResetToken
 *  com.billing.invoicehub.repository.PasswordResetTokenRepository
 *  org.springframework.data.jpa.repository.JpaRepository
 *  org.springframework.data.jpa.repository.Query
 *  org.springframework.data.repository.query.Param
 */
package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.PasswordResetToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository
extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Finds the most recent unused token for the given email.
     * Used for normal OTP verification flow.
     */
    Optional<PasswordResetToken> findFirstByEmailAndUsedFalseOrderByCreatedAtDesc(String email);

    /**
     * Finds the most recent token for the given email regardless of used status.
     * Used by verifyOtp to distinguish a brute-force-locked token (NOT_FOUND vs LOCKED).
     */
    Optional<PasswordResetToken> findFirstByEmailOrderByCreatedAtDesc(String email);
}

