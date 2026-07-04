/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.entity.Notification
 *  com.billing.invoicehub.repository.NotificationRepository
 *  org.springframework.data.jpa.repository.JpaRepository
 *  org.springframework.data.jpa.repository.Query
 *  org.springframework.data.repository.query.Param
 */
package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository
extends JpaRepository<Notification, Long> {
    @Query(value="SELECT n FROM Notification n\nWHERE n.user.id = :userId\nORDER BY n.createdAt DESC\n")
    public List<Notification> findByUserId(@Param(value="userId") Long var1);

    @Query(value="SELECT n FROM Notification n\nWHERE n.user.id = :userId AND n.isRead = false\nORDER BY n.createdAt DESC\n")
    public List<Notification> findUnreadByUserId(@Param(value="userId") Long var1);

    @Query(value="SELECT COUNT(n) FROM Notification n\nWHERE n.user.id = :userId AND n.isRead = false\n")
    public long countUnreadByUserId(@Param(value="userId") Long var1);

    public List<Notification> findTop10ByUser_IdOrderByCreatedAtDesc(Long var1);

    @Query(value="SELECT n FROM Notification n\nWHERE n.user.id = :userId AND n.relatedTicketId = :ticketId\nORDER BY n.createdAt DESC\n")
    public List<Notification> findByUserIdAndTicketId(@Param(value="userId") Long var1, @Param(value="ticketId") Long var2);

    @Query(value="SELECT n FROM Notification n\nWHERE n.user.id = :userId AND n.relatedInvoiceId = :invoiceId\nORDER BY n.createdAt DESC\n")
    public List<Notification> findByUserIdAndInvoiceId(@Param(value="userId") Long var1, @Param(value="invoiceId") Long var2);

    public void deleteByRelatedTicketId(Long var1);

    public void deleteByRelatedInvoiceId(Long var1);

    @Query("SELECT n FROM Notification n WHERE (n.user.id = :userId OR n.recipientRole IN :roles) AND (:unreadOnly = false OR n.isRead = false)")
    public org.springframework.data.domain.Page<Notification> findByUserOrRolesFiltered(
            @Param("userId") Long userId, 
            @Param("roles") java.util.Collection<String> roles, 
            @Param("unreadOnly") boolean unreadOnly, 
            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE (n.user.id = :userId OR n.recipientRole IN :roles) AND n.isRead = false")
    public long countUnreadByUserOrRoles(
            @Param("userId") Long userId, 
            @Param("roles") java.util.Collection<String> roles);
}

