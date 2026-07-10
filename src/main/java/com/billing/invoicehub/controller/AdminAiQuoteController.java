package com.billing.invoicehub.controller;

import com.billing.invoicehub.dto.AiQuotationEditDto;
import com.billing.invoicehub.entity.AiQuoteConversion;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.service.UserService;
import com.billing.invoicehub.service.AiQuoteConversionService;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin/ai-quote-converter")
public class AdminAiQuoteController {

    private static final Logger logger = LoggerFactory.getLogger(AdminAiQuoteController.class);

    private final AiQuoteConversionService aiQuoteConversionService;
    private final UserService userService;
    private final Gson gson;

    public AdminAiQuoteController(AiQuoteConversionService aiQuoteConversionService,
                                  UserService userService) {
        this.aiQuoteConversionService = aiQuoteConversionService;
        this.userService = userService;
        this.gson = new Gson();
    }

    // ─── GET /admin/ai-quote-converter (List History) ────────────────────────
    @GetMapping
    public String listHistory(Model model, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/admin/login";
        }

        List<AiQuoteConversion> conversions = aiQuoteConversionService.getAllConversions();
        model.addAttribute("conversions", conversions);
        return "admin-ai-quote-converter";
    }

    // ─── POST /admin/ai-quote-converter/upload ────────────────────────────────
    @PostMapping("/upload")
    public String uploadQuotation(@RequestParam("quoteFile") MultipartFile file,
                                  RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/admin/login";
        }

        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please select a quotation PDF to upload.");
            return "redirect:/admin/ai-quote-converter";
        }

        // Limit size to 20MB
        if (file.getSize() > 20 * 1024 * 1024) {
            redirectAttributes.addFlashAttribute("error", "File size exceeds the maximum limit of 20MB.");
            return "redirect:/admin/ai-quote-converter";
        }

        try {
            AiQuoteConversion result = aiQuoteConversionService.initiateConversion(file, currentUser.get());
            if ("FAILED".equals(result.getStatus())) {
                redirectAttributes.addFlashAttribute("error", "AI Extraction failed: " + result.getErrorMessage());
            } else {
                redirectAttributes.addFlashAttribute("message", "Supplier quote uploaded and processed by AI successfully.");
            }
        } catch (Exception e) {
            logger.error("Error during quote upload initiation: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "An unexpected error occurred during processing: " + e.getMessage());
        }

        return "redirect:/admin/ai-quote-converter";
    }

    // ─── GET /admin/ai-quote-converter/{id}/review ────────────────────────────
    @GetMapping("/{id}/review")
    public String reviewQuotation(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/admin/login";
        }

        Optional<AiQuoteConversion> conversionOpt = aiQuoteConversionService.getConversionById(id);
        if (conversionOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Quotation record not found.");
            return "redirect:/admin/ai-quote-converter";
        }

        AiQuoteConversion conversion = conversionOpt.get();
        if ("FAILED".equals(conversion.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "Cannot review a failed conversion.");
            return "redirect:/admin/ai-quote-converter";
        }

        AiQuotationEditDto editDto;
        if (conversion.getExtractedJson() != null && !conversion.getExtractedJson().isBlank()) {
            editDto = gson.fromJson(conversion.getExtractedJson(), AiQuotationEditDto.class);
        } else {
            editDto = new AiQuotationEditDto();
        }

        model.addAttribute("conversion", conversion);
        model.addAttribute("quoteForm", editDto);
        return "admin-ai-quote-converter-review";
    }

    // ─── POST /admin/ai-quote-converter/{id}/save-draft ───────────────────────
    @PostMapping("/{id}/save-draft")
    public String saveDraft(@PathVariable Long id,
                            @ModelAttribute("quoteForm") AiQuotationEditDto formDto,
                            RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/admin/login";
        }

        try {
            aiQuoteConversionService.saveDraft(id, formDto, currentUser.get());
            redirectAttributes.addFlashAttribute("message", "Draft saved successfully.");
        } catch (Exception e) {
            logger.error("Failed to save draft: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to save draft: " + e.getMessage());
            return "redirect:/admin/ai-quote-converter/" + id + "/review";
        }

        return "redirect:/admin/ai-quote-converter";
    }

    // ─── POST /admin/ai-quote-converter/{id}/approve ──────────────────────────
    @PostMapping("/{id}/approve")
    public String approveAndGenerate(@PathVariable Long id,
                                      @ModelAttribute("quoteForm") AiQuotationEditDto formDto,
                                      RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/admin/login";
        }

        try {
            AiQuoteConversion result = aiQuoteConversionService.approveAndGenerate(id, formDto, currentUser.get());
            redirectAttributes.addFlashAttribute("message", "Quotation approved and Flow Force Quote PDF generated successfully!");
            return "redirect:/admin/ai-quote-converter/" + id;
        } catch (Exception e) {
            logger.error("Failed to approve and generate quotation: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Approve and Generate failed: " + e.getMessage());
            return "redirect:/admin/ai-quote-converter/" + id + "/review";
        }
    }

    // ─── GET /admin/ai-quote-converter/{id} (Detailed View) ───────────────────
    @GetMapping("/{id}")
    public String viewDetail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/admin/login";
        }

        Optional<AiQuoteConversion> conversionOpt = aiQuoteConversionService.getConversionById(id);
        if (conversionOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Quotation record not found.");
            return "redirect:/admin/ai-quote-converter";
        }

        AiQuoteConversion conversion = conversionOpt.get();
        AiQuotationEditDto dataDto = null;
        if (conversion.getExtractedJson() != null && !conversion.getExtractedJson().isBlank()) {
            dataDto = gson.fromJson(conversion.getExtractedJson(), AiQuotationEditDto.class);
        }

        model.addAttribute("conversion", conversion);
        model.addAttribute("quoteData", dataDto);
        return "admin-ai-quote-converter-detail";
    }

    // ─── POST /admin/ai-quote-converter/{id}/delete ───────────────────────────
    @PostMapping("/{id}/delete")
    public String deleteConversion(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/admin/login";
        }

        try {
            aiQuoteConversionService.deleteConversion(id);
            redirectAttributes.addFlashAttribute("message", "Quotation conversion record deleted successfully.");
        } catch (Exception e) {
            logger.error("Failed to delete conversion record: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete record: " + e.getMessage());
        }

        return "redirect:/admin/ai-quote-converter";
    }

    // ─── Private Helper for current authenticated user ────────────────────────
    private Optional<AppUser> currentAppUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return Optional.empty();
        }
        return userService.findByUsername(authentication.getName());
    }
}
