/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.entity.AppUser
 *  com.billing.invoicehub.repository.AppUserRepository
 *  org.springframework.data.jpa.repository.JpaRepository
 *  org.springframework.data.jpa.repository.Query
 *  org.springframework.data.repository.query.Param
 */
package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository
extends JpaRepository<AppUser, Long> {
    public Optional<AppUser> findByUsername(String var1);

    public Optional<AppUser> findByVendorCode(String var1);

    public long countByVendorCodeStartingWith(String var1);

    @Query(value="select distinct u from AppUser u join u.roles r where r.name = :roleName order by u.id desc")
    public List<AppUser> findByRoles_NameOrderByIdDesc(@Param(value="roleName") String var1);
}

