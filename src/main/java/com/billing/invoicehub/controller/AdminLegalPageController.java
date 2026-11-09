package com.billing.invoicehub.controller;

import com.billing.invoicehub.entity.LegalDocument;
import com.billing.invoicehub.entity.LegalHistory;
import com.billing.invoicehub.service.LegalDocumentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/admin/legal-pages")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminLegalPageController {

    private final LegalDocumentService legalDocumentService;

    public AdminLegalPageController(LegalDocumentService legalDocumentService) {
        this.legalDocumentService = legalDocumentService;
    }

    @GetMapping
    public String viewLegalEditor(@RequestParam(value = "tab", required = false, defaultValue = "terms") String tab,
                                  @RequestParam(value = "saved", required = false) Boolean saved,
                                  Model model) {
        LegalDocument termsDoc = legalDocumentService.getDocument(LegalDocumentService.SLUG_TERMS).orElse(null);
        LegalDocument privacyDoc = legalDocumentService.getDocument(LegalDocumentService.SLUG_PRIVACY).orElse(null);
        LegalDocument cookiesDoc = legalDocumentService.getDocument(LegalDocumentService.SLUG_COOKIES).orElse(null);

        List<LegalHistory> termsHistory = legalDocumentService.getHistory(LegalDocumentService.SLUG_TERMS);
        List<LegalHistory> privacyHistory = legalDocumentService.getHistory(LegalDocumentService.SLUG_PRIVACY);
        List<LegalHistory> cookiesHistory = legalDocumentService.getHistory(LegalDocumentService.SLUG_COOKIES);

        model.addAttribute("activeTab", tab);
        model.addAttribute("saved", saved);

        model.addAttribute("termsDoc", termsDoc);
        model.addAttribute("privacyDoc", privacyDoc);
        model.addAttribute("cookiesDoc", cookiesDoc);

        model.addAttribute("termsHistory", termsHistory);
        model.addAttribute("privacyHistory", privacyHistory);
        model.addAttribute("cookiesHistory", cookiesHistory);

        return "admin/admin-legal-editor";
    }

    @PostMapping("/update")
    public String updateDocument(@RequestParam("slug") String slug,
                                 @RequestParam("version") String version,
                                 @RequestParam("titleEn") String titleEn,
                                 @RequestParam("titleId") String titleId,
                                 @RequestParam("contentEn") String contentEn,
                                 @RequestParam("contentId") String contentId,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {
        String username = principal != null ? principal.getName() : "super_admin";
        legalDocumentService.updateDocument(slug, version, titleEn, titleId, contentEn, contentId, username);

        redirectAttributes.addFlashAttribute("message", "Legal document '" + slug + "' updated successfully to version " + version);
        return "redirect:/admin/legal-pages?saved=true&tab=" + slug;
    }
}
