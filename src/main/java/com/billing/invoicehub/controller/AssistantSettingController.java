package com.billing.invoicehub.controller;

import com.billing.invoicehub.service.AssistantSettingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping({"/api/settings/assistant-vendors", "/admin/api/settings/assistant-vendors"})
public class AssistantSettingController {

    private final AssistantSettingService assistantSettingService;

    public AssistantSettingController(AssistantSettingService assistantSettingService) {
        this.assistantSettingService = assistantSettingService;
    }

    @GetMapping
    public ResponseEntity<?> getAssistantVendorsSetting() {
        return ResponseEntity.ok(Map.of("enabled", assistantSettingService.isAssistantForVendorsEnabled()));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> updateAssistantVendorsSetting(
            @RequestParam(value = "enabled", required = false) Boolean enabledParam,
            @RequestBody(required = false) Map<String, Object> body,
            Principal principal) {
        boolean enabled = false;
        if (enabledParam != null) {
            enabled = enabledParam;
        } else if (body != null && body.containsKey("enabled")) {
            enabled = Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        }
        String username = principal != null ? principal.getName() : "super_admin";
        assistantSettingService.setAssistantForVendorsEnabled(enabled, username);
        return ResponseEntity.ok(Map.of("success", true, "enabled", enabled));
    }
}

