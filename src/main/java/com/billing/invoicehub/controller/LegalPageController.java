package com.billing.invoicehub.controller;

import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.LegalDocument;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.service.LegalDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
import java.util.Optional;

@Controller
public class LegalPageController {

    private final LegalDocumentService legalDocumentService;
    private final AppUserRepository userRepository;

    public LegalPageController(LegalDocumentService legalDocumentService, AppUserRepository userRepository) {
        this.legalDocumentService = legalDocumentService;
        this.userRepository = userRepository;
    }

    @GetMapping("/terms")
    public String viewTerms(Authentication authentication, Model model) {
        checkAccess(authentication);
        Optional<LegalDocument> doc = legalDocumentService.getDocument(LegalDocumentService.SLUG_TERMS);
        model.addAttribute("doc", doc.orElse(null));
        model.addAttribute("slug", LegalDocumentService.SLUG_TERMS);
        return "legal/terms";
    }

    @GetMapping("/privacy")
    public String viewPrivacy(Authentication authentication, Model model) {
        checkAccess(authentication);
        Optional<LegalDocument> doc = legalDocumentService.getDocument(LegalDocumentService.SLUG_PRIVACY);
        model.addAttribute("doc", doc.orElse(null));
        model.addAttribute("slug", LegalDocumentService.SLUG_PRIVACY);
        return "legal/privacy";
    }

    @GetMapping("/cookies")
    public String viewCookies(Authentication authentication, Model model) {
        checkAccess(authentication);
        Optional<LegalDocument> doc = legalDocumentService.getDocument(LegalDocumentService.SLUG_COOKIES);
        model.addAttribute("doc", doc.orElse(null));
        model.addAttribute("slug", LegalDocumentService.SLUG_COOKIES);
        return "legal/cookies";
    }

    private void checkAccess(Authentication authentication) {
        if (legalDocumentService.isShowLegalPagesPublicly()) {
            return;
        }
        if (authentication != null && authentication.isAuthenticated()) {
            boolean isSuperAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
            if (isSuperAdmin) {
                return; // Preview allowed for SUPER_ADMIN
            }
        }
        throw new org.springframework.security.access.AccessDeniedException(
                "Legal pages are currently in preview mode for SUPER_ADMIN only.");
    }

    @GetMapping("/api/legal/status")
    @ResponseBody
    public ResponseEntity<?> getLegalStatus(Authentication authentication) {
        if (!legalDocumentService.isRequireTermsAcceptance()) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal()),
                    "needsAcceptance", false
            ));
        }

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", false,
                    "needsAcceptance", false
            ));
        }

        String username = authentication.getName();
        Optional<AppUser> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", true,
                    "needsAcceptance", false
            ));
        }

        AppUser user = userOpt.get();
        boolean accepted = legalDocumentService.hasUserAcceptedCurrentTerms(user);
        String currentTerms = legalDocumentService.getCurrentVersion(LegalDocumentService.SLUG_TERMS);
        String currentPrivacy = legalDocumentService.getCurrentVersion(LegalDocumentService.SLUG_PRIVACY);

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "needsAcceptance", !accepted,
                "termsVersion", currentTerms,
                "privacyVersion", currentPrivacy,
                "userVersion", user.getTermsAcceptedVersion() != null ? user.getTermsAcceptedVersion() : ""
        ));
    }

    @PostMapping("/api/legal/accept")
    @ResponseBody
    public ResponseEntity<?> acceptTerms(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        String username = authentication.getName();
        Optional<AppUser> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            AppUser user = userOpt.get();
            String clientIp = request.getHeader("X-Forwarded-For");
            if (clientIp == null || clientIp.isBlank()) {
                clientIp = request.getRemoteAddr();
            } else {
                clientIp = clientIp.split(",")[0].trim();
            }
            legalDocumentService.recordAcceptance(user, clientIp);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "termsVersion", legalDocumentService.getCurrentVersion(LegalDocumentService.SLUG_TERMS)
            ));
        }

        return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
    }
}

