/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.entity.AppRole
 *  com.billing.invoicehub.repository.AppRoleRepository
 *  org.springframework.data.jpa.repository.JpaRepository
 */
package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.AppRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppRoleRepository
extends JpaRepository<AppRole, Long> {
    public Optional<AppRole> findByName(String var1);
}

