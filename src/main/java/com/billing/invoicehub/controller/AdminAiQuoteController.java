package com.billing.invoicehub.controller;

import com.billing.invoicehub.dto.AiChatMessageDto;
import com.billing.invoicehub.dto.AiQuoteSessionDto;
import com.billing.invoicehub.dto.AiQuotationEditDto;
import com.billing.invoicehub.entity.AiQuoteConversion;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.service.AiQuoteConversionService;
import com.billing.invoicehub.service.AiQuoteSessionService;
import com.billing.invoicehub.service.UserService;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/admin/ai-quote-converter")
public class AdminAiQuoteController {

    private static final Logger logger = LoggerFactory.getLogger(AdminAiQuoteController.class);

    private final AiQuoteConversionService aiQuoteConversionService;
    private final AiQuoteSessionService aiQuoteSessionService;
    private final UserService userService;
    private final Gson gson;

    public AdminAiQuoteController(
            AiQuoteConversionService aiQuoteConversionService,
            AiQuoteSessionService aiQuoteSessionService,
            UserService userService) {
        this.aiQuoteConversionService = aiQuoteConversionService;
        this.aiQuoteSessionService = aiQuoteSessionService;
        this.userService = userService;
        this.gson = new Gson();
    }

    // ─── GET /admin/ai-quote-converter (Main Conversational AI Assistant UI) ──
    @GetMapping
    public String listHistory(@RequestParam(value = "sessionId", required = false) Long activeSessionId,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/admin/login";
        }

        List<AiQuoteSessionDto> sessions = aiQuoteSessionService.getUserSessions(currentUser.get());
        model.addAttribute("sessions", sessions);

        // Include legacy conversion history as fallback/audit reference
        List<AiQuoteConversion> conversions = aiQuoteConversionService.getAllConversions();
        model.addAttribute("conversions", conversions);

        AiQuoteSessionDto activeSession = null;
        if (activeSessionId != null) {
            Optional<AiQuoteSessionDto> sOpt = aiQuoteSessionService.getSessionDto(activeSessionId, currentUser.get());
            if (sOpt.isPresent()) {
                activeSession = sOpt.get();
            }
        }
        model.addAttribute("activeSession", activeSession);

        return "admin-ai-quote-converter";
    }

    // ─── REST ENDPOINTS FOR AI CHAT WORKFLOW ─────────────────────────────────

    @PostMapping("/api/sessions")
    @ResponseBody
    public ResponseEntity<?> createSession(@RequestBody(required = false) Map<String, String> payload) {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        String title = payload != null ? payload.get("title") : "New Quotation Assistant";
        AiQuoteSessionDto sessionDto = aiQuoteSessionService.createSession(currentUser.get(), title);
        return ResponseEntity.ok(sessionDto);
    }

    @GetMapping("/api/sessions")
    @ResponseBody
    public ResponseEntity<?> getSessions() {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        List<AiQuoteSessionDto> sessions = aiQuoteSessionService.getUserSessions(currentUser.get());
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/api/sessions/{id}")
    @ResponseBody
    public ResponseEntity<?> getSession(@PathVariable Long id) {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        Optional<AiQuoteSessionDto> sessionOpt = aiQuoteSessionService.getSessionDto(id, currentUser.get());
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Session not found or access denied"));
        }
        return ResponseEntity.ok(sessionOpt.get());
    }

    @PostMapping("/api/sessions/{id}/messages")
    @ResponseBody
    public ResponseEntity<?> sendMessage(
            @PathVariable Long id,
            @RequestParam(value = "message", required = false) String messageText,
            @RequestParam(value = "file", required = false) MultipartFile file) {

        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        try {
            AiChatMessageDto responseMsg = aiQuoteSessionService.sendMessage(id, messageText, file, currentUser.get());
            return ResponseEntity.ok(responseMsg);
        } catch (Exception e) {
            logger.error("Failed to send AI chat message for session {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process message: " + e.getMessage()));
        }
    }

    @PostMapping("/api/sessions/{id}/update-quote")
    @ResponseBody
    public ResponseEntity<?> updateStructuredQuote(
            @PathVariable Long id,
            @RequestBody AiQuotationEditDto updatedQuote) {

        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        try {
            AiQuoteSessionDto updatedSession = aiQuoteSessionService.updateStructuredQuote(id, updatedQuote, currentUser.get());
            return ResponseEntity.ok(updatedSession);
        } catch (Exception e) {
            logger.error("Failed to update quote structure for session {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Update quote failed: " + e.getMessage()));
        }
    }

    @PostMapping("/api/sessions/{id}/generate")
    @ResponseBody
    public ResponseEntity<?> generateFlowForceQuote(@PathVariable Long id,
                                                    @RequestParam(value = "format", required = false, defaultValue = "PDF") String formatStr) {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        try {
            com.billing.invoicehub.enums.OutputFormat format = com.billing.invoicehub.enums.OutputFormat.fromString(formatStr);
            AiQuoteSessionDto sessionDto = aiQuoteSessionService.generateFlowForceQuote(id, format, currentUser.get());
            return ResponseEntity.ok(sessionDto);
        } catch (Exception e) {
            logger.error("Failed to generate Flow Force quote for session {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Quotation generation failed: " + e.getMessage()));
        }
    }

    @DeleteMapping("/api/sessions/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteSession(@PathVariable Long id) {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        boolean deleted = aiQuoteSessionService.deleteSession(id, currentUser.get());
        if (deleted) {
            return ResponseEntity.ok(Map.of("success", true));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Session not found or access denied"));
        }
    }

    // ─── LEGACY COMPATIBILITY ENDPOINTS (Preserved for backwards compatibility) ─

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

    @PostMapping("/{id}/approve")
    public String approveAndGenerate(@PathVariable Long id,
                                      @ModelAttribute("quoteForm") AiQuotationEditDto formDto,
                                      @RequestParam(value = "outputFormat", required = false, defaultValue = "PDF") String outputFormatStr,
                                      RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/admin/login";
        }

        try {
            if (formDto == null || formDto.getCustomerName() == null || formDto.getCustomerName().isBlank()) {
                Optional<AiQuoteConversion> convOpt = aiQuoteConversionService.getConversionById(id);
                if (convOpt.isPresent() && convOpt.get().getExtractedJson() != null) {
                    formDto = gson.fromJson(convOpt.get().getExtractedJson(), AiQuotationEditDto.class);
                }
            }
            com.billing.invoicehub.enums.OutputFormat format = com.billing.invoicehub.enums.OutputFormat.fromString(outputFormatStr);
            aiQuoteConversionService.approveAndGenerate(id, formDto, format, currentUser.get());
            redirectAttributes.addFlashAttribute("message", "Quotation generated successfully!");
            return "redirect:/admin/ai-quote-converter/" + id;
        } catch (Exception e) {
            logger.error("Failed to approve and generate quotation: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Generation failed: " + e.getMessage());
            return "redirect:/admin/ai-quote-converter/" + id + "/review";
        }
    }

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

    @PostMapping("/{id}/delete")
    public String deleteConversion(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/admin/login";
        }

        try {
            aiQuoteConversionService.deleteConversion(id);
            redirectAttributes.addFlashAttribute("message", "Quotation record deleted successfully.");
        } catch (Exception e) {
            logger.error("Failed to delete quotation record: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Delete failed: " + e.getMessage());
        }

        return "redirect:/admin/ai-quote-converter";
    }

    private Optional<AppUser> currentAppUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String username = auth.getName();
            return userService.findByUsername(username);
        }
        return Optional.empty();
    }
}
