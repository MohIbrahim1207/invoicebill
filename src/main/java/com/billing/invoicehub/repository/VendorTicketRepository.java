/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.entity.TicketStatus
 *  com.billing.invoicehub.entity.VendorTicket
 *  com.billing.invoicehub.repository.VendorTicketRepository
 *  org.springframework.data.jpa.repository.JpaRepository
 *  org.springframework.data.jpa.repository.Query
 *  org.springframework.data.repository.query.Param
 */
package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.TicketStatus;
import com.billing.invoicehub.entity.VendorTicket;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VendorTicketRepository
extends JpaRepository<VendorTicket, Long> {
    @Query(value="select t from VendorTicket t\nwhere t.owner.id = :ownerId\n  and (:ticketNo is null or lower(t.ticketNo) like lower(concat('%', :ticketNo, '%')))\n  and (:invoiceNo is null or lower(t.invoiceNo) like lower(concat('%', :invoiceNo, '%')))\n  and (:year is null or function('year', t.invoiceDate) = :year)\n  and (:status is null or t.statusRequest = :status)\norder by t.id desc\n")
    public List<VendorTicket> searchTicketsByOwner(@Param(value="ownerId") Long var1, @Param(value="ticketNo") String var2, @Param(value="invoiceNo") String var3, @Param(value="year") Integer var4, @Param(value="status") TicketStatus var5);

    @Query(value="select t from VendorTicket t\nwhere (:ticketNo is null or lower(t.ticketNo) like lower(concat('%', :ticketNo, '%')))\n  and (:invoiceNo is null or lower(t.invoiceNo) like lower(concat('%', :invoiceNo, '%')))\n  and (:year is null or function('year', t.invoiceDate) = :year)\n  and (:status is null or t.statusRequest = :status)\norder by t.id desc\n")
    public List<VendorTicket> searchTickets(@Param(value="ticketNo") String var1, @Param(value="invoiceNo") String var2, @Param(value="year") Integer var3, @Param(value="status") TicketStatus var4);

    @Query(value="select distinct function('year', t.invoiceDate)\nfrom VendorTicket t\nwhere t.owner.id = :ownerId\norder by function('year', t.invoiceDate) desc\n")
    public List<Integer> findAvailableYearsByOwnerId(@Param(value="ownerId") Long var1);

    @Query(value="select distinct function('year', t.invoiceDate)\nfrom VendorTicket t\norder by function('year', t.invoiceDate) desc\n")
    public List<Integer> findAvailableYears();

    public long countByCreatedAtBetween(LocalDateTime var1, LocalDateTime var2);
}

