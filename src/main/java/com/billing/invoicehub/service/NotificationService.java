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

import com.billing.invoicehub.dto.NotificationDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Collection;
import java.util.Collections;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(NotificationRepository notificationRepository, SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public Notification createNotification(String title, String message, NotificationType type, AppUser user) {
        Notification notification = new Notification(title, message, type, user);
        Notification saved = this.notificationRepository.save(notification);
        // Optional: Broadcast if needed, but standard single user notification
        return saved;
    }

    @Transactional
    public Notification createNotification(String title, String message, NotificationType type, AppUser user, Long ticketId, Long invoiceId) {
        Notification notification = new Notification(title, message, type, user);
        notification.setRelatedTicketId(ticketId);
        notification.setRelatedInvoiceId(invoiceId);
        if (ticketId != null) {
            notification.setReferenceId(ticketId);
            notification.setReferenceType("TICKET");
        } else if (invoiceId != null) {
            notification.setReferenceId(invoiceId);
            notification.setReferenceType("INVOICE");
        }
        return this.notificationRepository.save(notification);
    }

    @Transactional
    public Notification createNotification(String title, String message, NotificationType type, String recipientRole, Long referenceId, String referenceType) {
        Notification notification = new Notification(title, message, type, recipientRole);
        notification.setReferenceId(referenceId);
        notification.setReferenceType(referenceType);
        if ("TICKET".equals(referenceType)) {
            notification.setRelatedTicketId(referenceId);
        } else if ("INVOICE".equals(referenceType)) {
            notification.setRelatedInvoiceId(referenceId);
        }
        Notification saved = this.notificationRepository.save(notification);
        
        if ("ROLE_ADMIN".equals(recipientRole)) {
            try {
                messagingTemplate.convertAndSend("/topic/admin-notifications", new NotificationDto(saved));
                log.info("Successfully broadcasted notification to /topic/admin-notifications");
            } catch (Exception ex) {
                log.error("Failed to broadcast notification: {}", ex.getMessage(), ex);
            }
        }
        return saved;
    }

    @Transactional(readOnly=true)
    public Page<NotificationDto> getUserNotifications(Long userId, Collection<String> roles, boolean unreadOnly, Pageable pageable) {
        Collection<String> queryRoles = roles != null ? roles : Collections.emptyList();
        return this.notificationRepository.findByUserOrRolesFiltered(userId, queryRoles, unreadOnly, pageable)
                .map(NotificationDto::new);
    }

    @Transactional(readOnly=true)
    public long getUnreadCount(Long userId, Collection<String> roles) {
        Collection<String> queryRoles = roles != null ? roles : Collections.emptyList();
        return this.notificationRepository.countUnreadByUserOrRoles(userId, queryRoles);
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
    public boolean markAsRead(Long notificationId, Long userId, Collection<String> roles) {
        Optional<Notification> notification = this.notificationRepository.findById(notificationId);
        if (notification.isPresent()) {
            Notification n = notification.get();
            // Verify ownership
            if ((n.getUser() != null && n.getUser().getId().equals(userId)) || 
                (n.getRecipientRole() != null && roles.contains(n.getRecipientRole()))) {
                n.setRead(true);
                this.notificationRepository.save(n);
                log.info("Notification {} marked as read for user {}", notificationId, userId);
                return true;
            }
        }
        return false;
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        List<Notification> unreadNotifications = this.notificationRepository.findUnreadByUserId(userId);
        unreadNotifications.forEach(n -> n.setRead(true));
        this.notificationRepository.saveAll(unreadNotifications);
        log.info("All notifications marked as read for user {}", userId);
    }

    @Transactional
    public void markAllAsRead(Long userId, Collection<String> roles) {
        Collection<String> queryRoles = roles != null ? roles : Collections.emptyList();
        // Retrieve unread notifications and save them as read
        Page<Notification> unread = this.notificationRepository.findByUserOrRolesFiltered(userId, queryRoles, true, Pageable.unpaged());
        unread.forEach(n -> n.setRead(true));
        this.notificationRepository.saveAll(unread);
        log.info("All notifications marked as read for user {} and roles {}", userId, roles);
    }

    @Transactional
    public void deleteNotification(Long notificationId) {
        this.notificationRepository.deleteById(notificationId);
        log.info("Notification {} deleted", notificationId);
    }

    @Transactional
    public boolean deleteNotification(Long notificationId, Long userId, Collection<String> roles) {
        Optional<Notification> notification = this.notificationRepository.findById(notificationId);
        if (notification.isPresent()) {
            Notification n = notification.get();
            // Verify ownership
            if ((n.getUser() != null && n.getUser().getId().equals(userId)) || 
                (n.getRecipientRole() != null && roles.contains(n.getRecipientRole()))) {
                this.notificationRepository.delete(n);
                log.info("Notification {} deleted by user {}", notificationId, userId);
                return true;
            }
        }
        return false;
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

