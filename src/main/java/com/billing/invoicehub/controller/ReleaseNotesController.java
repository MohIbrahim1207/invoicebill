package com.billing.invoicehub.controller;

import com.billing.invoicehub.dto.ReleaseNoteDto;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.service.MaintenanceBannerService;
import com.billing.invoicehub.service.ReleaseNotesService;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReleaseNotesController {
    private static final Logger log = LoggerFactory.getLogger(ReleaseNotesController.class);

    private final ReleaseNotesService releaseNotesService;
    private final AppUserRepository userRepository;
    private final MaintenanceBannerService maintenanceBannerService;

    public ReleaseNotesController(ReleaseNotesService releaseNotesService,
                                  AppUserRepository userRepository,
                                  MaintenanceBannerService maintenanceBannerService) {
        this.releaseNotesService = releaseNotesService;
        this.userRepository = userRepository;
        this.maintenanceBannerService = maintenanceBannerService;
    }

    @GetMapping(value = {"/api/release-notes/latest-unread", "/admin/api/release-notes/latest-unread"})
    public ResponseEntity<Map<String, Object>> getLatestUnreadReleaseNotes(Principal principal) {
        Map<String, Object> response = new HashMap<>();
        if (principal == null) {
            response.put("hasUnread", false);
            return ResponseEntity.ok(response);
        }

        Optional<AppUser> userOpt = userRepository.findFirstByUsername(principal.getName());
        if (userOpt.isEmpty()) {
            response.put("hasUnread", false);
            return ResponseEntity.ok(response);
        }

        AppUser user = userOpt.get();
        boolean hasUnread = releaseNotesService.hasUnreadNotes(user);
        response.put("hasUnread", hasUnread);
        response.put("lastSeenVersion", user.getLastSeenVersion());
        response.put("audience", releaseNotesService.resolveAudience(user));

        if (hasUnread) {
            releaseNotesService.getLatestNoteForUser(user).ifPresent(note -> {
                response.put("releaseNote", note);
            });
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = {"/api/release-notes/acknowledge", "/admin/api/release-notes/acknowledge"})
    public ResponseEntity<Map<String, Object>> acknowledgeReleaseNotes(@RequestParam("version") String version,
                                                                       Principal principal) {
        Map<String, Object> response = new HashMap<>();
        if (principal == null) {
            response.put("success", false);
            response.put("message", "Not authenticated");
            return ResponseEntity.badRequest().body(response);
        }

        boolean ok = releaseNotesService.acknowledgeVersion(principal.getName(), version);
        response.put("success", ok);
        response.put("version", version);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = {"/api/release-notes/all", "/admin/api/release-notes/all"})
    public ResponseEntity<Map<String, Object>> getAllReleaseNotes(Principal principal) {
        Map<String, Object> response = new HashMap<>();
        String audience = "ALL";
        if (principal != null) {
            Optional<AppUser> userOpt = userRepository.findFirstByUsername(principal.getName());
            if (userOpt.isPresent()) {
                audience = releaseNotesService.resolveAudience(userOpt.get());
            }
        }
        List<ReleaseNoteDto> notes = releaseNotesService.getNotesForAudience(audience);
        response.put("audience", audience);
        response.put("notes", notes);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = {"/api/maintenance-banner", "/admin/api/maintenance-banner"})
    public ResponseEntity<Map<String, Object>> getMaintenanceBanner() {
        Map<String, Object> response = new HashMap<>();
        response.put("active", maintenanceBannerService.isBannerActive());
        response.put("messageEn", maintenanceBannerService.getBannerMessageEn());
        response.put("messageId", maintenanceBannerService.getBannerMessageId());
        response.put("type", maintenanceBannerService.getBannerType());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/admin/api/maintenance-banner")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> updateMaintenanceBanner(
            @RequestParam("enabled") boolean enabled,
            @RequestParam(value = "messageEn", required = false) String messageEn,
            @RequestParam(value = "messageId", required = false) String messageId,
            @RequestParam(value = "type", defaultValue = "warning") String type,
            Principal principal) {

        String updatedBy = principal != null ? principal.getName() : "admin";
        maintenanceBannerService.updateBanner(enabled, messageEn, messageId, type, updatedBy);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("active", enabled);
        response.put("messageEn", maintenanceBannerService.getBannerMessageEn());
        response.put("messageId", maintenanceBannerService.getBannerMessageId());
        response.put("type", maintenanceBannerService.getBannerType());
        return ResponseEntity.ok(response);
    }
}
