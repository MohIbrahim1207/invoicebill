package com.billing.invoicehub.controller;

import com.billing.invoicehub.service.AssistantSettingService;
import com.billing.invoicehub.service.EmailService;
import com.billing.invoicehub.service.EmailSettingService;
import com.billing.invoicehub.service.LegalDocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.Map;

@Controller
@RequestMapping({"/admin/settings", "/admin/api/settings"})
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminSettingsController {

    private final EmailSettingService emailSettingService;
    private final EmailService emailService;
    private final AssistantSettingService assistantSettingService;
    private final LegalDocumentService legalDocumentService;

    public AdminSettingsController(EmailSettingService emailSettingService,
                                   EmailService emailService,
                                   AssistantSettingService assistantSettingService,
                                   LegalDocumentService legalDocumentService) {
        this.emailSettingService = emailSettingService;
        this.emailService = emailService;
        this.assistantSettingService = assistantSettingService;
        this.legalDocumentService = legalDocumentService;
    }

    @GetMapping
    public String viewSettings(@RequestParam(value = "saved", required = false) Boolean saved,
                               Model model) {
        model.addAttribute("notificationCc", emailSettingService.getNotificationCcSetting());
        model.addAttribute("notificationCopyMode", emailSettingService.getNotificationCopyMode());
        model.addAttribute("defaultNotificationCc", EmailSettingService.DEFAULT_NOTIFICATION_CC);
        model.addAttribute("defaultNotificationCopyMode", EmailSettingService.DEFAULT_NOTIFICATION_COPY_MODE);
        model.addAttribute("appBaseUrl", emailService.getLoginUrl().replace("/login", ""));
        model.addAttribute("loginUrl", emailService.getLoginUrl());
        model.addAttribute("assistantVendorsEnabled", assistantSettingService.isAssistantForVendorsEnabled());
        model.addAttribute("requireTermsAcceptance", legalDocumentService.isRequireTermsAcceptance());
        model.addAttribute("showLegalPagesPublicly", legalDocumentService.isShowLegalPagesPublicly());
        model.addAttribute("saved", saved);

        return "admin/admin-settings";
    }

    @PostMapping("/email-cc")
    public String updateEmailCc(@RequestParam("notificationCc") String notificationCc,
                                @RequestParam(value = "copyMode", defaultValue = "BCC") String copyMode,
                                Principal principal,
                                RedirectAttributes redirectAttributes) {
        String username = principal != null ? principal.getName() : "super_admin";
        emailSettingService.setNotificationCcSetting(notificationCc, username);
        emailSettingService.setNotificationCopyMode(copyMode, username);
        redirectAttributes.addFlashAttribute("message", "Email notification settings updated successfully.");
        return "redirect:/admin/settings?saved=true";
    }

    @GetMapping("/email-cc")
    @ResponseBody
    public ResponseEntity<?> getEmailCc() {
        return ResponseEntity.ok(Map.of(
                "notificationCc", emailSettingService.getNotificationCcSetting(),
                "notificationCopyMode", emailSettingService.getNotificationCopyMode(),
                "defaultCc", EmailSettingService.DEFAULT_NOTIFICATION_CC,
                "defaultCopyMode", EmailSettingService.DEFAULT_NOTIFICATION_COPY_MODE,
                "parsedRecipients", emailSettingService.getNotificationCcRecipients("sample@example.com")
        ));
    }

    @PostMapping("/email-cc/api")
    @ResponseBody
    public ResponseEntity<?> updateEmailCcApi(@RequestBody Map<String, String> body,
                                             Principal principal) {
        String cc = body.getOrDefault("notificationCc", EmailSettingService.DEFAULT_NOTIFICATION_CC);
        String copyMode = body.getOrDefault("copyMode", EmailSettingService.DEFAULT_NOTIFICATION_COPY_MODE);
        String username = principal != null ? principal.getName() : "super_admin";
        emailSettingService.setNotificationCcSetting(cc, username);
        emailSettingService.setNotificationCopyMode(copyMode, username);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "notificationCc", emailSettingService.getNotificationCcSetting(),
                "notificationCopyMode", emailSettingService.getNotificationCopyMode()
        ));
    }
}

