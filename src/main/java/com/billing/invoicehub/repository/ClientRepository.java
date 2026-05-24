/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.entity.Client
 *  com.billing.invoicehub.repository.ClientRepository
 *  org.springframework.data.jpa.repository.JpaRepository
 */
package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.Client;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository
extends JpaRepository<Client, Long> {
    public List<Client> findByOwner_Id(Long var1);

    public List<Client> findAllByCompanyNameIgnoreCase(String var1);

    public Optional<Client> findByCompanyNameIgnoreCaseAndOwner_Id(String var1, Long var2);

    public Optional<Client> findByCompanyNameIgnoreCase(String var1);
}

