/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.entity.Invoice
 *  com.billing.invoicehub.repository.InvoiceRepository
 *  org.springframework.data.jpa.repository.JpaRepository
 */
package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.Invoice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository
extends JpaRepository<Invoice, Long> {
    public List<Invoice> findAllByOrderByIdDesc();

    public List<Invoice> findByClientIdOrderByIdDesc(Long var1);

    public List<Invoice> findByClient_Owner_IdOrderByIdDesc(Long var1);

    public List<Invoice> findByClient_IdAndClient_Owner_IdOrderByIdDesc(Long var1, Long var2);
}

