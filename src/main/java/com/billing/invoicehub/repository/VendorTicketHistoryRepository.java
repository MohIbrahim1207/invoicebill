/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.entity.VendorTicketHistory
 *  com.billing.invoicehub.repository.VendorTicketHistoryRepository
 *  org.springframework.data.jpa.repository.JpaRepository
 *  org.springframework.data.jpa.repository.Query
 *  org.springframework.data.repository.query.Param
 */
package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.VendorTicketHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VendorTicketHistoryRepository
extends JpaRepository<VendorTicketHistory, Long> {
    @Query(value="select h from VendorTicketHistory h\nwhere h.ticket.id = :ticketId\norder by h.changedAt desc\n")
    public List<VendorTicketHistory> findByTicketIdOrderByChangedAtDesc(@Param(value="ticketId") Long var1);
}

