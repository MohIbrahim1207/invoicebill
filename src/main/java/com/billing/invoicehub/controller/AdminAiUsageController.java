package com.billing.invoicehub.controller;

import com.billing.invoicehub.service.AiUsageService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Controller
@RequestMapping("/admin/ai-usage")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminAiUsageController {

    private final AiUsageService aiUsageService;

    public AdminAiUsageController(AiUsageService aiUsageService) {
        this.aiUsageService = aiUsageService;
    }

    @GetMapping
    public String viewDashboard(
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "settingsSaved", required = false) Boolean settingsSaved,
            Model model) {

        Map<String, Object> data = aiUsageService.getDashboardData(startDate, endDate);
        model.addAllAttributes(data);
        model.addAttribute("settingsSaved", settingsSaved);

        return "admin/admin-ai-usage";
    }

    @PostMapping("/settings")
    public String updateSettings(
            @RequestParam("priceInput1M") double priceInput1M,
            @RequestParam("priceOutput1M") double priceOutput1M,
            @RequestParam("exchangeRate") double exchangeRate,
            @RequestParam("monthlyBudget") double monthlyBudget,
            @RequestParam(value = "autoDisableVendor", defaultValue = "false") boolean autoDisableVendor,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        String username = principal != null ? principal.getName() : "super_admin";
        aiUsageService.updateSettings(priceInput1M, priceOutput1M, exchangeRate, monthlyBudget, autoDisableVendor, username);

        redirectAttributes.addFlashAttribute("message", "AI Usage settings updated successfully.");
        return "redirect:/admin/ai-usage?settingsSaved=true";
    }

    @GetMapping("/export-csv")
    public void exportCsv(
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletResponse response) throws IOException {

        LocalDate s = startDate != null ? startDate : LocalDate.now().minusDays(29);
        LocalDate e = endDate != null ? endDate : LocalDate.now();

        String filename = String.format("ai-usage-%s-to-%s.csv",
                s.format(DateTimeFormatter.ISO_DATE),
                e.format(DateTimeFormatter.ISO_DATE));

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        aiUsageService.exportCsv(s, e, response.getWriter());
    }
}
