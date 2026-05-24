/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.entity.AppUser
 *  com.billing.invoicehub.entity.Notification
 *  com.billing.invoicehub.entity.NotificationType
 *  com.billing.invoicehub.repository.NotificationRepository
 *  com.billing.invoicehub.service.NotificationService
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 */
package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.Notification;
import com.billing.invoicehub.entity.NotificationType;
import com.billing.invoicehub.repository.NotificationRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public Notification createNotification(String title, String message, NotificationType type, AppUser user) {
        Notification notification = new Notification(title, message, type, user);
        return this.notificationRepository.save(notification);
    }

    @Transactional
    public Notification createNotification(String title, String message, NotificationType type, AppUser user, Long ticketId, Long invoiceId) {
        Notification notification = new Notification(title, message, type, user);
        notification.setRelatedTicketId(ticketId);
        notification.setRelatedInvoiceId(invoiceId);
        return this.notificationRepository.save(notification);
    }

    @Transactional(readOnly=true)
    public List<Notification> getNotificationsByUserId(Long userId) {
        return this.notificationRepository.findByUserId(userId);
    }

    @Transactional(readOnly=true)
    public List<Notification> getUnreadNotificationsByUserId(Long userId) {
        return this.notificationRepository.findUnreadByUserId(userId);
    }

    @Transactional(readOnly=true)
    public long getUnreadCount(Long userId) {
        return this.notificationRepository.countUnreadByUserId(userId);
    }

    @Transactional(readOnly=true)
    public List<Notification> getRecentNotifications(Long userId) {
        return this.notificationRepository.findTop10ByUser_IdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        Optional<Notification> notification = this.notificationRepository.findById(notificationId);
        if (notification.isPresent()) {
            notification.get().setRead(true);
            this.notificationRepository.save(notification.get());
            log.info("Notification {} marked as read", notificationId);
        }
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        List<Notification> unreadNotifications = this.notificationRepository.findUnreadByUserId(userId);
        unreadNotifications.forEach(n -> n.setRead(true));
        this.notificationRepository.saveAll(unreadNotifications);
        log.info("All notifications marked as read for user {}", userId);
    }

    @Transactional
    public void deleteNotification(Long notificationId) {
        this.notificationRepository.deleteById(notificationId);
        log.info("Notification {} deleted", notificationId);
    }

    @Transactional
    public void deleteNotificationsByTicket(Long ticketId) {
        this.notificationRepository.deleteByRelatedTicketId(ticketId);
        log.info("All notifications for ticket {} deleted", (Object)ticketId);
    }

    @Transactional
    public void deleteNotificationsByInvoice(Long invoiceId) {
        this.notificationRepository.deleteByRelatedInvoiceId(invoiceId);
        log.info("All notifications for invoice {} deleted", (Object)invoiceId);
    }

    @Transactional(readOnly=true)
    public Optional<Notification> getNotificationById(Long id) {
        return this.notificationRepository.findById(id);
    }

    @Transactional(readOnly=true)
    public List<Notification> getTicketNotifications(Long userId, Long ticketId) {
        return this.notificationRepository.findByUserIdAndTicketId(userId, ticketId);
    }

    @Transactional(readOnly=true)
    public List<Notification> getInvoiceNotifications(Long userId, Long invoiceId) {
        return this.notificationRepository.findByUserIdAndInvoiceId(userId, invoiceId);
    }
}

