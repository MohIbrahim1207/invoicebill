package com.billing.invoicehub.controller;

import com.billing.invoicehub.dto.NotificationDto;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.service.UserService;
import com.billing.invoicehub.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class NotificationController {
    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationController(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @GetMapping("/notifications")
    public String notificationsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            Authentication authentication, 
            Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        AppUser user = this.userService.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<NotificationDto> notificationsPage = this.notificationService.getUserNotifications(user.getId(), roles, unreadOnly, pageable);
        long unreadCount = this.notificationService.getUnreadCount(user.getId(), roles);

        model.addAttribute("notificationsPage", notificationsPage);
        model.addAttribute("unreadCount", unreadCount);
        model.addAttribute("unreadOnly", unreadOnly);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", notificationsPage.getTotalPages());
        model.addAttribute("totalItems", notificationsPage.getTotalElements());

        return "notifications";
    }

    // --- REST APIs for UI bell dropdown & real-time actions ---

    @GetMapping("/api/notifications")
    @ResponseBody
    public ResponseEntity<Page<NotificationDto>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        AppUser user = this.userService.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<NotificationDto> notifications = this.notificationService.getUserNotifications(user.getId(), roles, unreadOnly, pageable);
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/api/notifications/unread-count")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> unreadCount(Authentication authentication) {
        Map<String, Object> resp = new HashMap<>();
        if (authentication == null || !authentication.isAuthenticated()) {
            resp.put("unreadCount", 0);
            return ResponseEntity.ok(resp);
        }
        AppUser user = this.userService.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            resp.put("unreadCount", 0);
            return ResponseEntity.ok(resp);
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        long count = this.notificationService.getUnreadCount(user.getId(), roles);
        resp.put("unreadCount", count);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/{id}/read")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable Long id, Authentication authentication) {
        Map<String, Object> resp = new HashMap<>();
        if (authentication == null || !authentication.isAuthenticated()) {
            resp.put("status", "error");
            resp.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(resp);
        }
        AppUser user = this.userService.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            resp.put("status", "error");
            resp.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(resp);
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        boolean success = this.notificationService.markAsRead(id, user.getId(), roles);
        if (!success) {
            resp.put("status", "error");
            resp.put("message", "Not found or forbidden");
            return ResponseEntity.status(403).body(resp);
        }
        resp.put("status", "success");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/read-all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markAllRead(Authentication authentication) {
        Map<String, Object> resp = new HashMap<>();
        if (authentication == null || !authentication.isAuthenticated()) {
            resp.put("status", "error");
            resp.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(resp);
        }
        AppUser user = this.userService.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            resp.put("status", "error");
            resp.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(resp);
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        this.notificationService.markAllAsRead(user.getId(), roles);
        resp.put("status", "success");
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping("/api/notifications/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteNotification(@PathVariable Long id, Authentication authentication) {
        Map<String, Object> resp = new HashMap<>();
        if (authentication == null || !authentication.isAuthenticated()) {
            resp.put("status", "error");
            resp.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(resp);
        }
        AppUser user = this.userService.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            resp.put("status", "error");
            resp.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(resp);
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        boolean success = this.notificationService.deleteNotification(id, user.getId(), roles);
        if (!success) {
            resp.put("status", "error");
            resp.put("message", "Not found or forbidden");
            return ResponseEntity.status(403).body(resp);
        }
        resp.put("status", "success");
        return ResponseEntity.ok(resp);
    }
}
