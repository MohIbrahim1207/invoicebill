/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.entity.PurchaseOrder
 *  com.billing.invoicehub.repository.PurchaseOrderRepository
 *  org.springframework.data.jpa.repository.JpaRepository
 */
package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.PurchaseOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository
extends JpaRepository<PurchaseOrder, Long> {
    public Optional<PurchaseOrder> findByPoNumber(String var1);

    public Optional<PurchaseOrder> findByPoNumberAndVendor_Id(String var1, Long var2);

    @EntityGraph(attributePaths={"vendor"})
    public Optional<PurchaseOrder> findWithVendorById(Long var1);

    public List<PurchaseOrder> findByVendor_Id(Long var1);

    public List<PurchaseOrder> findByVendor_IdOrderByCreatedAtDesc(Long var1);

    public List<PurchaseOrder> findByActiveOrderByCreatedAtDesc(boolean var1);

    @EntityGraph(attributePaths={"vendor"})
    public List<PurchaseOrder> findWithVendorByActiveOrderByCreatedAtDesc(boolean var1);
}

