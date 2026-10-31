package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.DashboardStatsDto;
import com.billing.invoicehub.entity.Invoice;
import com.billing.invoicehub.repository.InvoiceRepository;
import com.billing.invoicehub.util.FormatUtils;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final InvoiceRepository invoiceRepository;
    private final MessageSource messageSource;

    public DashboardService(InvoiceRepository invoiceRepository, MessageSource messageSource) {
        this.invoiceRepository = invoiceRepository;
        this.messageSource = messageSource;
    }

    public DashboardStatsDto getDashboardStats() {
        DashboardStatsDto dto = new DashboardStatsDto();

        long total = invoiceRepository.count();
        long pending = invoiceRepository.countByStatusIgnoreCase("Pending");
        long approved = invoiceRepository.countByStatusIgnoreCase("Approved");
        BigDecimal totalAmount = invoiceRepository.sumAllAmount();
        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;
        }

        dto.setTotalInvoices(total);
        dto.setPendingInvoices(pending);
        dto.setApprovedInvoices(approved);
        dto.setTotalAmount(totalAmount);
        dto.setTotalAmountFull(FormatUtils.formatIDRFull(totalAmount));
        dto.setTotalAmountCompact(FormatUtils.formatIDRCompact(totalAmount));

        // 1. Monthly volume (last 6 months)
        Locale locale = LocaleContextHolder.getLocale();
        if (locale == null) {
            locale = new Locale("id", "ID");
        }
        DateTimeFormatter labelFormatter = DateTimeFormatter.ofPattern("MMM yyyy", locale);

        YearMonth currentMonth = YearMonth.now();
        List<YearMonth> last6Months = new ArrayList<>();
        Map<YearMonth, Long> countsByMonth = new LinkedHashMap<>();
        Map<YearMonth, BigDecimal> amountsByMonth = new LinkedHashMap<>();

        for (int i = 5; i >= 0; i--) {
            YearMonth ym = currentMonth.minusMonths(i);
            last6Months.add(ym);
            countsByMonth.put(ym, 0L);
            amountsByMonth.put(ym, BigDecimal.ZERO);
        }

        // 2. Status distribution
        Map<String, Long> statusCountsMap = new LinkedHashMap<>();
        statusCountsMap.put("Pending", 0L);
        statusCountsMap.put("Approved", 0L);
        statusCountsMap.put("Partially Paid", 0L);
        statusCountsMap.put("Revised", 0L);
        statusCountsMap.put("Released", 0L);

        List<Invoice> allInvoices = invoiceRepository.findAll();
        for (Invoice inv : allInvoices) {
            // Tally status
            String status = inv.getStatus() != null && !inv.getStatus().isBlank() ? inv.getStatus().trim() : "Pending";
            statusCountsMap.put(status, statusCountsMap.getOrDefault(status, 0L) + 1L);

            // Tally monthly volume
            String dateStr = inv.getInvoiceDate();
            if (dateStr != null && !dateStr.isBlank()) {
                try {
                    YearMonth ym = parseYearMonth(dateStr.trim());
                    if (ym != null && countsByMonth.containsKey(ym)) {
                        countsByMonth.put(ym, countsByMonth.get(ym) + 1L);
                        BigDecimal amt = inv.getAmount() != null ? inv.getAmount() : BigDecimal.ZERO;
                        amountsByMonth.put(ym, amountsByMonth.get(ym).add(amt));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // Populate monthly arrays in DTO
        for (YearMonth ym : last6Months) {
            dto.getMonthlyLabels().add(ym.format(labelFormatter));
            dto.getMonthlyCounts().add(countsByMonth.get(ym));
            BigDecimal amt = amountsByMonth.get(ym);
            dto.getMonthlyAmounts().add(amt);
            dto.getMonthlyAmountsFormatted().add(FormatUtils.formatIDRCompact(amt));
        }

        // Populate status arrays in DTO
        Map<String, String> statusColorPalette = new HashMap<>();
        statusColorPalette.put("Pending", "#f59e0b");       // amber
        statusColorPalette.put("Approved", "#10b981");      // green
        statusColorPalette.put("Partially Paid", "#6366f1");// indigo
        statusColorPalette.put("Revised", "#ef4444");       // red
        statusColorPalette.put("Released", "#3b82f6");      // blue

        for (Map.Entry<String, Long> entry : statusCountsMap.entrySet()) {
            if (entry.getValue() > 0 || (total == 0 && (entry.getKey().equals("Pending") || entry.getKey().equals("Approved")))) {
                String localizedStatus = localizeStatus(entry.getKey(), locale);
                dto.getStatusLabels().add(localizedStatus);
                dto.getStatusCounts().add(entry.getValue());
                dto.getStatusColors().add(statusColorPalette.getOrDefault(entry.getKey(), "#94a3b8"));
            }
        }

        // Fallback for empty state so donut renders cleanly
        if (dto.getStatusLabels().isEmpty()) {
            String emptyLabel = messageSource.getMessage("invoice.empty", null, "No Invoices", locale);
            dto.getStatusLabels().add(emptyLabel);
            dto.getStatusCounts().add(0L);
            dto.getStatusColors().add("#cbd5e1");
        }

        return dto;
    }

    private String localizeStatus(String status, Locale locale) {
        if (status == null) return "Pending";
        String key = switch (status.trim().toLowerCase()) {
            case "pending" -> "invoice.filter.pending";
            case "approved" -> "invoice.filter.approved";
            case "partially paid" -> "invoice.filter.partially_paid";
            case "revised" -> "invoice.filter.revised";
            case "released" -> "invoice.filter.released";
            default -> null;
        };
        if (key != null) {
            return messageSource.getMessage(key, null, status, locale);
        }
        return status;
    }

    private YearMonth parseYearMonth(String str) {
        if (str.length() >= 7) {
            String sub = str.substring(0, 7);
            if (sub.matches("\\d{4}-\\d{2}")) {
                return YearMonth.parse(sub);
            }
        }
        try {
            LocalDate d = LocalDate.parse(str);
            return YearMonth.from(d);
        } catch (Exception e) {
            return null;
        }
    }
}

