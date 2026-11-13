package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.DashboardStatsDto;
import com.billing.invoicehub.dto.DashboardStatsDto.*;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.AuditLog;
import com.billing.invoicehub.entity.Invoice;
import com.billing.invoicehub.entity.SystemSetting;
import com.billing.invoicehub.entity.TicketStatus;
import com.billing.invoicehub.entity.VendorTicket;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.AuditLogRepository;
import com.billing.invoicehub.repository.InvoiceRepository;
import com.billing.invoicehub.repository.SystemSettingRepository;
import com.billing.invoicehub.repository.VendorTicketRepository;
import com.billing.invoicehub.util.FormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final InvoiceRepository invoiceRepository;
    private final MessageSource messageSource;
    private final VendorTicketRepository vendorTicketRepository;
    private final AuditLogRepository auditLogRepository;
    private final AppUserRepository userRepository;
    private final SystemSettingRepository settingRepository;

    @Value("${app.dashboard.pending-days-threshold:7}")
    private int defaultPendingThresholdDays = 7;

    @Autowired
    public DashboardService(InvoiceRepository invoiceRepository,
                            MessageSource messageSource,
                            @Autowired(required = false) VendorTicketRepository vendorTicketRepository,
                            @Autowired(required = false) AuditLogRepository auditLogRepository,
                            @Autowired(required = false) AppUserRepository userRepository,
                            @Autowired(required = false) SystemSettingRepository settingRepository) {
        this.invoiceRepository = invoiceRepository;
        this.messageSource = messageSource;
        this.vendorTicketRepository = vendorTicketRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.settingRepository = settingRepository;
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

        // 2. Status distribution & Rupiah amounts per status
        Map<String, Long> statusCountsMap = new LinkedHashMap<>();
        Map<String, BigDecimal> statusAmountsMap = new LinkedHashMap<>();
        for (String st : List.of("Pending", "In Progress", "Revised", "Approved", "Partially Paid", "Released", "Rejected")) {
            statusCountsMap.put(st, 0L);
            statusAmountsMap.put(st, BigDecimal.ZERO);
        }

        List<Invoice> allInvoices = invoiceRepository.findAll();
        for (Invoice inv : allInvoices) {
            // Tally status
            String rawStatus = inv.getStatus() != null && !inv.getStatus().isBlank() ? inv.getStatus().trim() : "Pending";
            String status = normalizeStatusKey(rawStatus);
            statusCountsMap.put(status, statusCountsMap.getOrDefault(status, 0L) + 1L);

            BigDecimal amt = inv.getAmount() != null ? inv.getAmount() : BigDecimal.ZERO;
            statusAmountsMap.put(status, statusAmountsMap.getOrDefault(status, BigDecimal.ZERO).add(amt));

            // Tally monthly volume using LocalDate
            LocalDate date = inv.getInvoiceDate();
            if (date != null) {
                YearMonth ym = YearMonth.from(date);
                if (countsByMonth.containsKey(ym)) {
                    countsByMonth.put(ym, countsByMonth.get(ym) + 1L);
                    amountsByMonth.put(ym, amountsByMonth.get(ym).add(amt));
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
        statusColorPalette.put("In Progress", "#1d4ed8");   // blue
        statusColorPalette.put("Approved", "#0d9488");      // blue-green / teal
        statusColorPalette.put("Partially Paid", "#0284c7");// sky blue
        statusColorPalette.put("Revised", "#ec4899");       // vibrant pink / magenta
        statusColorPalette.put("Released", "#16a34a");      // solid green
        statusColorPalette.put("Paid", "#16a34a");          // solid green
        statusColorPalette.put("Rejected", "#ef4444");      // red
        statusColorPalette.put("Cancelled", "#64748b");     // slate

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

        // 3. Amount by Status breakdown (Rupiah per status)
        computeStatusAmounts(dto, statusCountsMap, statusAmountsMap, totalAmount, locale, statusColorPalette);

        // 4. Needs Attention Panel
        computeNeedsAttention(dto);

        // 5. Top 5 Vendors this month by amount
        computeTopVendorsThisMonth(dto, allInvoices);

        // 6. Recent Activity (last 10 audit events, linked)
        computeRecentActivities(dto, locale);

        return dto;
    }

    private String normalizeStatusKey(String status) {
        if (status == null || status.isBlank()) return "Pending";
        String s = status.trim();
        if (s.equalsIgnoreCase("open") || s.equalsIgnoreCase("pending")) return "Pending";
        if (s.equalsIgnoreCase("in progress") || s.equalsIgnoreCase("in_progress")) return "In Progress";
        if (s.equalsIgnoreCase("revised") || s.equalsIgnoreCase("revise")) return "Revised";
        if (s.equalsIgnoreCase("approved")) return "Approved";
        if (s.equalsIgnoreCase("partially paid") || s.equalsIgnoreCase("partially_paid")) return "Partially Paid";
        if (s.equalsIgnoreCase("released") || s.equalsIgnoreCase("paid") || s.equalsIgnoreCase("resolved")) return "Released";
        if (s.equalsIgnoreCase("rejected") || s.equalsIgnoreCase("cancel") || s.equalsIgnoreCase("cancelled")) return "Rejected";
        return s;
    }

    private void computeStatusAmounts(DashboardStatsDto dto,
                                      Map<String, Long> countsMap,
                                      Map<String, BigDecimal> amountsMap,
                                      BigDecimal totalAmount,
                                      Locale locale,
                                      Map<String, String> colors) {
        List<StatusAmountDto> list = new ArrayList<>();
        for (Map.Entry<String, Long> entry : countsMap.entrySet()) {
            String status = entry.getKey();
            long count = entry.getValue();
            BigDecimal amount = amountsMap.getOrDefault(status, BigDecimal.ZERO);
            if (count > 0 || list.isEmpty()) {
                StatusAmountDto sa = new StatusAmountDto();
                sa.setStatus(status);
                sa.setLabel(localizeStatus(status, locale));
                sa.setColor(colors.getOrDefault(status, "#64748b"));
                sa.setPillClass(getPillClassForStatus(status));
                sa.setCount(count);
                sa.setAmount(amount);
                sa.setFormattedAmount(FormatUtils.formatAmount(amount, "IDR"));
                double pct = (totalAmount != null && totalAmount.compareTo(BigDecimal.ZERO) > 0)
                        ? amount.divide(totalAmount, 4, RoundingMode.HALF_UP).doubleValue() * 100.0
                        : 0.0;
                sa.setPercentage(pct);
                sa.setFormattedPercentage(String.format(Locale.US, "%.1f%%", pct));
                list.add(sa);
            }
        }
        dto.setStatusAmounts(list);
    }

    private String getPillClassForStatus(String status) {
        if (status == null) return "status-pill-pending";
        return switch (status.trim().toLowerCase()) {
            case "pending", "open" -> "status-pill-pending";
            case "in progress", "in_progress" -> "status-pill-in-progress";
            case "revised", "revise" -> "status-pill-revised";
            case "approved" -> "status-pill-approved";
            case "partially paid", "partially_paid" -> "status-pill-partially-paid";
            case "released", "paid", "resolved" -> "status-pill-paid";
            case "rejected", "cancel", "cancelled" -> "status-pill-rejected";
            case "unsynced" -> "status-pill-unsynced";
            default -> "status-pill-neutral";
        };
    }

    private void computeNeedsAttention(DashboardStatsDto dto) {
        NeedsAttentionDto na = dto.getNeedsAttention();
        int threshold = defaultPendingThresholdDays;
        if (settingRepository != null) {
            Optional<SystemSetting> setting = settingRepository.findById("dashboard_pending_threshold_days");
            if (setting.isPresent()) {
                try {
                    threshold = Integer.parseInt(setting.get().getValue().trim());
                } catch (Exception ignored) {}
            }
        }
        na.setPendingThresholdDays(threshold);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(threshold);

        if (vendorTicketRepository != null) {
            try {
                long pendingOverdue = vendorTicketRepository.countByStatusRequestAndCreatedAtBefore(TicketStatus.OPEN, cutoff);
                na.setPendingOverdueCount(pendingOverdue);
                na.setUnsyncedCount(vendorTicketRepository.countUnsyncedTickets());
                na.setRevisedCount(vendorTicketRepository.countByStatusRequest(TicketStatus.REVISE));
            } catch (Exception e) {
                // Graceful fallback for mock tests
            }
        }

        if (userRepository != null) {
            try {
                List<AppUser> vendors = userRepository.findByRoles_NameOrderByIdDesc("ROLE_VENDOR");
                long unverified = vendors.stream()
                        .filter(v -> !v.isVerified() && (v.getRejectionReason() == null || v.getRejectionReason().isBlank()))
                        .count();
                na.setUnverifiedVendorsCount(unverified);
            } catch (Exception e) {
                // Graceful fallback
            }
        }
    }

    private void computeTopVendorsThisMonth(DashboardStatsDto dto, List<Invoice> allInvoices) {
        YearMonth currentYm = YearMonth.now();

        // Map: VendorKey -> Aggregator
        class VendorAgg {
            Long vendorId;
            String name;
            String code;
            long count = 0;
            BigDecimal amount = BigDecimal.ZERO;
        }

        Map<String, VendorAgg> aggMap = new LinkedHashMap<>();

        // Aggregate from invoices this month
        for (Invoice inv : allInvoices) {
            if (inv.getInvoiceDate() != null && YearMonth.from(inv.getInvoiceDate()).equals(currentYm)) {
                AppUser vendor = null;
                if (inv.getClient() != null && inv.getClient().getOwner() != null) {
                    vendor = inv.getClient().getOwner();
                } else if (vendorTicketRepository != null) {
                    try {
                        VendorTicket vt = vendorTicketRepository.findByInvoice_Id(inv.getId()).orElse(null);
                        if (vt != null && vt.getVendor() != null) {
                            vendor = vt.getVendor();
                        }
                    } catch (Exception ignored) {}
                }

                String key;
                String name;
                String code = null;
                Long vId = null;

                if (vendor != null) {
                    vId = vendor.getId();
                    key = "V-" + vId;
                    name = vendor.getCompanyName() != null && !vendor.getCompanyName().isBlank()
                            ? vendor.getCompanyName()
                            : vendor.getUsername();
                    code = vendor.getVendorCode();
                } else if (inv.getClient() != null && inv.getClient().getCompanyName() != null) {
                    key = "C-" + inv.getClient().getCompanyName();
                    name = inv.getClient().getCompanyName();
                } else {
                    key = "OTHER";
                    name = "Other Vendor";
                }

                VendorAgg agg = aggMap.computeIfAbsent(key, k -> new VendorAgg());
                agg.vendorId = vId;
                agg.name = name;
                agg.code = code;
                agg.count++;
                if (inv.getAmount() != null) {
                    agg.amount = agg.amount.add(inv.getAmount());
                }
            }
        }

        // Also aggregate any unsynced tickets for this month
        if (vendorTicketRepository != null) {
            try {
                List<VendorTicket> tickets = vendorTicketRepository.findAll();
                for (VendorTicket t : tickets) {
                    if (t.getInvoice() == null && t.getStatusRequest() != TicketStatus.CANCEL) {
                        LocalDate d = t.getInvoiceDate() != null ? t.getInvoiceDate() : (t.getCreatedAt() != null ? t.getCreatedAt().toLocalDate() : null);
                        if (d != null && YearMonth.from(d).equals(currentYm)) {
                            AppUser vendor = t.getVendor();
                            String key = vendor != null ? "V-" + vendor.getId() : (t.getClient() != null ? "C-" + t.getClient().getCompanyName() : "T-" + t.getId());
                            VendorAgg agg = aggMap.computeIfAbsent(key, k -> new VendorAgg());
                            if (vendor != null) {
                                agg.vendorId = vendor.getId();
                                agg.name = vendor.getCompanyName() != null && !vendor.getCompanyName().isBlank() ? vendor.getCompanyName() : vendor.getUsername();
                                agg.code = vendor.getVendorCode();
                            } else if (t.getClient() != null) {
                                agg.name = t.getClient().getCompanyName();
                            } else {
                                agg.name = "Vendor";
                            }
                            agg.count++;
                            BigDecimal amt = t.getTotal() != null ? t.getTotal() : (t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO);
                            agg.amount = agg.amount.add(amt);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        List<VendorAgg> sorted = new ArrayList<>(aggMap.values());
        sorted.sort((a, b) -> b.amount.compareTo(a.amount));

        List<TopVendorDto> topList = new ArrayList<>();
        int rank = 1;
        for (VendorAgg v : sorted) {
            if (rank > 5) break;
            TopVendorDto item = new TopVendorDto();
            item.setRank(rank++);
            item.setVendorId(v.vendorId);
            item.setVendorName(v.name);
            item.setVendorCode(v.code);
            item.setInvoiceCount(v.count);
            item.setTotalAmount(v.amount);
            item.setFormattedAmount(FormatUtils.formatAmount(v.amount, "IDR"));
            topList.add(item);
        }
        dto.setTopVendors(topList);
    }

    private void computeRecentActivities(DashboardStatsDto dto, Locale locale) {
        if (auditLogRepository == null) return;
        try {
            List<AuditLog> logs = auditLogRepository.findTop10ByOrderByCreatedAtDesc();
            List<RecentActivityDto> result = new ArrayList<>();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM, HH:mm", locale);

            for (AuditLog log : logs) {
                RecentActivityDto a = new RecentActivityDto();
                a.setId(log.getId());
                a.setAction(log.getAction() != null ? log.getAction() : "Activity");
                a.setUsername(log.getUsername() != null ? log.getUsername() : "System");
                a.setRole(log.getRole() != null ? log.getRole().replace("ROLE_", "") : "USER");
                a.setEntityType(log.getEntityType());
                a.setEntityId(log.getEntityId());
                a.setTimestamp(log.getCreatedAt());
                a.setFormattedTime(log.getCreatedAt() != null ? log.getCreatedAt().format(fmt) : "");

                // Description
                String desc = log.getNewValue();
                if (desc == null || desc.isBlank() || desc.length() > 120) {
                    desc = log.getAction() + (log.getEntityType() != null ? " on " + log.getEntityType() : "");
                }
                a.setDescription(desc);

                // Target URL
                String targetUrl = "/admin/audit-log";
                if ("VendorTicket".equalsIgnoreCase(log.getEntityType()) && log.getEntityId() != null) {
                    targetUrl = "/tickets/" + log.getEntityId() + "/manage";
                } else if ("Invoice".equalsIgnoreCase(log.getEntityType()) && log.getEntityId() != null) {
                    targetUrl = "/invoice/" + log.getEntityId();
                } else if ("AppUser".equalsIgnoreCase(log.getEntityType()) && log.getEntityId() != null) {
                    targetUrl = (log.getAction() != null && log.getAction().toLowerCase().contains("vendor"))
                            ? "/admin/vendors/" + log.getEntityId()
                            : "/admin/users";
                } else if ("AI_ASSISTANT".equalsIgnoreCase(log.getEntityType()) || "AI_TOOL".equalsIgnoreCase(log.getEntityType())) {
                    targetUrl = "/admin/ai-quote-converter";
                }
                a.setTargetUrl(targetUrl);

                result.add(a);
            }
            dto.setRecentActivities(result);
        } catch (Exception ignored) {}
    }

    private String localizeStatus(String status, Locale locale) {
        if (status == null) return "Pending";
        String key = switch (status.trim().toLowerCase()) {
            case "pending", "open" -> "invoice.filter.pending";
            case "in progress", "in_progress" -> "invoice.filter.in_progress";
            case "approved" -> "invoice.filter.approved";
            case "partially paid", "partially_paid" -> "invoice.filter.partially_paid";
            case "revised", "revise" -> "invoice.filter.revised";
            case "released", "paid", "resolved" -> "invoice.filter.released";
            case "rejected", "cancel", "cancelled" -> "invoice.filter.rejected";
            default -> null;
        };
        if (key != null) {
            return messageSource.getMessage(key, null, status, locale);
        }
        return status;
    }
}
