/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.controller.NotificationController
 *  com.billing.invoicehub.entity.AppUser
 *  com.billing.invoicehub.repository.AppUserRepository
 *  com.billing.invoicehub.service.NotificationService
 *  org.springframework.http.ResponseEntity
 *  org.springframework.security.core.Authentication
 *  org.springframework.stereotype.Controller
 *  org.springframework.ui.Model
 *  org.springframework.web.bind.annotation.DeleteMapping
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.ResponseBody
 */
package com.billing.invoicehub.controller;

import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.Notification;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.service.NotificationService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class NotificationController {
    private final NotificationService notificationService;
    private final AppUserRepository appUserRepository;

    public NotificationController(NotificationService notificationService, AppUserRepository appUserRepository) {
        this.notificationService = notificationService;
        this.appUserRepository = appUserRepository;
    }

    @GetMapping(value={"/notifications"})
    public String notificationsPage(Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        AppUser user = this.appUserRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }
        List<Notification> notifications = this.notificationService.getNotificationsByUserId(user.getId());
        long unreadCount = this.notificationService.getUnreadCount(user.getId());
        model.addAttribute("notifications", (Object)notifications);
        model.addAttribute("unreadCount", (Object)unreadCount);
        return "notifications";
    }

    @GetMapping(value={"/notifications/recent"})
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> recentNotifications(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.ok(List.of());
        }
        AppUser user = this.appUserRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.ok(List.of());
        }
        List<Notification> recent = this.notificationService.getRecentNotifications(user.getId());
        List<Map<String, Object>> payload = recent.stream().map(n -> {
            HashMap<String, Object> m = new HashMap<String, Object>();
            m.put("id", n.getId());
            m.put("title", n.getTitle());
            m.put("message", n.getMessage());
            m.put("read", n.isRead());
            m.put("notificationType", n.getNotificationType() != null ? n.getNotificationType().name() : null);
            m.put("createdAt", n.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(payload);
    }

    @GetMapping(value={"/notifications/unread-count"})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> unreadCount(Authentication authentication) {
        HashMap<String, Object> resp = new HashMap<String, Object>();
        if (authentication == null || !authentication.isAuthenticated()) {
            resp.put("unreadCount", 0);
            return ResponseEntity.ok(resp);
        }
        AppUser user = this.appUserRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            resp.put("unreadCount", 0);
            return ResponseEntity.ok(resp);
        }
        long count = this.notificationService.getUnreadCount(user.getId());
        resp.put("unreadCount", count);
        return ResponseEntity.ok(resp);
    }

    @PostMapping(value={"/notifications/{id}/read"})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable Long id, Authentication authentication) {
        HashMap<String, Object> resp = new HashMap<String, Object>();
        if (authentication == null || !authentication.isAuthenticated()) {
            resp.put("status", "error");
            resp.put("message", "Unauthorized");
            return ResponseEntity.status((int)401).body(resp);
        }
        AppUser user = this.appUserRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            resp.put("status", "error");
            resp.put("message", "Unauthorized");
            return ResponseEntity.status((int)401).body(resp);
        }
        Optional<Notification> notification = this.notificationService.getNotificationById(id);
        if (notification.isPresent() && !notification.get().getUser().getId().equals(user.getId())) {
            resp.put("status", "error");
            resp.put("message", "Forbidden");
            return ResponseEntity.status((int)403).body(resp);
        }
        this.notificationService.markAsRead(id);
        resp.put("status", "success");
        return ResponseEntity.ok(resp);
    }

    @PostMapping(value={"/notifications/read-all"})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markAllRead(Authentication authentication) {
        HashMap<String, Object> resp = new HashMap<String, Object>();
        if (authentication == null || !authentication.isAuthenticated()) {
            resp.put("status", "error");
            resp.put("message", "Unauthorized");
            return ResponseEntity.status((int)401).body(resp);
        }
        AppUser user = this.appUserRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            resp.put("status", "error");
            resp.put("message", "Unauthorized");
            return ResponseEntity.status((int)401).body(resp);
        }
        this.notificationService.markAllAsRead(user.getId());
        resp.put("status", "success");
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping(value={"/notifications/{id}"})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteNotification(@PathVariable Long id, Authentication authentication) {
        HashMap<String, Object> resp = new HashMap<String, Object>();
        if (authentication == null || !authentication.isAuthenticated()) {
            resp.put("status", "error");
            resp.put("message", "Unauthorized");
            return ResponseEntity.status((int)401).body(resp);
        }
        AppUser user = this.appUserRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            resp.put("status", "error");
            resp.put("message", "Unauthorized");
            return ResponseEntity.status((int)401).body(resp);
        }
        Optional<Notification> notification = this.notificationService.getNotificationById(id);
        if (notification.isPresent() && !notification.get().getUser().getId().equals(user.getId())) {
            resp.put("status", "error");
            resp.put("message", "Forbidden");
            return ResponseEntity.status((int)403).body(resp);
        }
        this.notificationService.deleteNotification(id);
        resp.put("status", "success");
        return ResponseEntity.ok(resp);
    }
}

