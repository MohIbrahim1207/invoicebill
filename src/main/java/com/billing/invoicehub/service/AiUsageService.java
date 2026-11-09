package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.AiUsageLog;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.NotificationType;
import com.billing.invoicehub.entity.SystemSetting;
import com.billing.invoicehub.repository.AiUsageLogRepository;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.SystemSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AiUsageService {

    private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);

    // Setting keys
    public static final String KEY_PRICE_INPUT_1M = "ai_pricing_input_per_million";
    public static final String KEY_PRICE_OUTPUT_1M = "ai_pricing_output_per_million";
    public static final String KEY_EXCHANGE_RATE = "ai_usd_to_idr_rate";
    public static final String KEY_MONTHLY_BUDGET = "ai_monthly_budget_idr";
    public static final String KEY_AUTO_DISABLE_VENDOR = "ai_auto_disable_vendor_at_budget";
    public static final String KEY_LAST_ALERT_MONTH = "ai_budget_alert_last_month";
    public static final String KEY_LAST_ALERT_LEVEL = "ai_budget_alert_last_level"; // 80 or 100

    // Defaults
    public static final double DEFAULT_PRICE_INPUT_1M = 0.075;   // USD per 1M tokens (Gemini 2.5 Flash / 1.5 Flash)
    public static final double DEFAULT_PRICE_OUTPUT_1M = 0.30;   // USD per 1M tokens
    public static final double DEFAULT_EXCHANGE_RATE = 16000.0;  // 1 USD = 16,000 IDR
    public static final double DEFAULT_MONTHLY_BUDGET = 500000.0; // Rp 500,000 / month

    private final AiUsageLogRepository usageLogRepository;
    private final SystemSettingRepository settingRepository;
    private final NotificationService notificationService;
    private final AssistantSettingService assistantSettingService;
    private final AppUserRepository userRepository;
    private final AuditLogService auditLogService;

    public AiUsageService(AiUsageLogRepository usageLogRepository,
                           SystemSettingRepository settingRepository,
                           @Autowired(required = false) NotificationService notificationService,
                           @Autowired(required = false) AssistantSettingService assistantSettingService,
                           AppUserRepository userRepository,
                           @Autowired(required = false) AuditLogService auditLogService) {
        this.usageLogRepository = usageLogRepository;
        this.settingRepository = settingRepository;
        this.notificationService = notificationService;
        this.assistantSettingService = assistantSettingService;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    // ─── Settings Accessors ──────────────────────────────────────────────────

    public double getPriceInputPerMillion() {
        return getDoubleSetting(KEY_PRICE_INPUT_1M, DEFAULT_PRICE_INPUT_1M);
    }

    public double getPriceOutputPerMillion() {
        return getDoubleSetting(KEY_PRICE_OUTPUT_1M, DEFAULT_PRICE_OUTPUT_1M);
    }

    public double getExchangeRateUsdToIdr() {
        return getDoubleSetting(KEY_EXCHANGE_RATE, DEFAULT_EXCHANGE_RATE);
    }

    public double getMonthlyBudgetIdr() {
        return getDoubleSetting(KEY_MONTHLY_BUDGET, DEFAULT_MONTHLY_BUDGET);
    }

    public boolean isAutoDisableVendorAtBudget() {
        return "true".equalsIgnoreCase(getStringSetting(KEY_AUTO_DISABLE_VENDOR, "false"));
    }

    @Transactional
    public void updateSettings(double inputPer1M, double outputPer1M, double exchangeRate, double monthlyBudget,
                               boolean autoDisableVendor, String updatedBy) {
        saveSetting(KEY_PRICE_INPUT_1M, String.valueOf(inputPer1M), updatedBy);
        saveSetting(KEY_PRICE_OUTPUT_1M, String.valueOf(outputPer1M), updatedBy);
        saveSetting(KEY_EXCHANGE_RATE, String.valueOf(exchangeRate), updatedBy);
        saveSetting(KEY_MONTHLY_BUDGET, String.valueOf(monthlyBudget), updatedBy);
        saveSetting(KEY_AUTO_DISABLE_VENDOR, String.valueOf(autoDisableVendor), updatedBy);

        if (auditLogService != null) {
            auditLogService.log(
                    updatedBy != null ? updatedBy : "super_admin",
                    "ROLE_SUPER_ADMIN",
                    "UPDATE_AI_USAGE_SETTINGS",
                    "SYSTEM_SETTING",
                    null,
                    null,
                    String.format("Updated AI settings: input/1M=$%.4f, output/1M=$%.4f, rate=Rp %.0f, budget=Rp %.0f, autoDisableVendor=%b",
                            inputPer1M, outputPer1M, exchangeRate, monthlyBudget, autoDisableVendor)
            );
        }
    }

    // ─── Logging Every Gemini API Call ───────────────────────────────────────

    /**
     * Logs Gemini usage without prompt or response text for privacy compliance.
     */
    @Transactional
    public AiUsageLog logUsage(String username, String role, String feature, String model,
                               int inputTokens, int outputTokens, long durationMs,
                               boolean success, String errorType) {
        AiUsageLog entry = new AiUsageLog();
        entry.setCreatedAt(LocalDateTime.now());
        entry.setUsername(username != null ? username : "anonymous");
        entry.setRole(role != null ? role : "ROLE_USER");
        entry.setFeature(feature != null ? feature : "OTHER");
        entry.setModel(model != null ? model : "gemini-2.5-flash");
        entry.setInputTokens(Math.max(0, inputTokens));
        entry.setOutputTokens(Math.max(0, outputTokens));
        entry.setTotalTokens(entry.getInputTokens() + entry.getOutputTokens());
        entry.setDurationMs(durationMs);
        entry.setSuccess(success);
        entry.setErrorType(errorType);

        // Calculate Cost
        double priceIn = getPriceInputPerMillion();
        double priceOut = getPriceOutputPerMillion();
        double rate = getExchangeRateUsdToIdr();

        double costUsd = (entry.getInputTokens() / 1_000_000.0 * priceIn) + (entry.getOutputTokens() / 1_000_000.0 * priceOut);
        double costIdr = costUsd * rate;

        entry.setEstimatedCostUsd(BigDecimal.valueOf(costUsd).setScale(6, RoundingMode.HALF_UP));
        entry.setEstimatedCostIdr(BigDecimal.valueOf(costIdr).setScale(2, RoundingMode.HALF_UP));

        AiUsageLog saved = usageLogRepository.save(entry);

        // Check budget thresholds asynchronously or post-save
        try {
            checkBudgetThresholds();
        } catch (Exception ex) {
            log.warn("Failed checking AI budget thresholds: {}", ex.getMessage());
        }

        return saved;
    }

    // ─── Budget Alert & Auto-Shutoff ─────────────────────────────────────────

    public synchronized void checkBudgetThresholds() {
        YearMonth currentYm = YearMonth.now();
        LocalDateTime monthStart = currentYm.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = currentYm.atEndOfMonth().atTime(LocalTime.MAX);

        BigDecimal monthTotalCost = usageLogRepository.sumCostIdrBetween(monthStart, monthEnd);
        if (monthTotalCost == null) {
            monthTotalCost = BigDecimal.ZERO;
        }

        double budget = getMonthlyBudgetIdr();
        if (budget <= 0) return;

        double spent = monthTotalCost.doubleValue();
        double ratio = spent / budget;

        String currentMonthStr = currentYm.toString(); // "2026-11"
        String lastAlertMonth = getStringSetting(KEY_LAST_ALERT_MONTH, "");
        String lastAlertLevel = getStringSetting(KEY_LAST_ALERT_LEVEL, "0");

        boolean isNewMonth = !currentMonthStr.equals(lastAlertMonth);
        int currentLevelRecorded = isNewMonth ? 0 : Integer.parseInt(lastAlertLevel.matches("\\d+") ? lastAlertLevel : "0");

        if (ratio >= 1.0 && currentLevelRecorded < 100) {
            // 100% budget exceeded
            notifySuperAdmins(
                    "AI Monthly Budget Exceeded (100%)",
                    String.format("Estimated AI spend of Rp %,.0f has exceeded the monthly budget of Rp %,.0f (%.1f%% used).",
                            spent, budget, ratio * 100),
                    NotificationType.ALERT
            );
            saveSetting(KEY_LAST_ALERT_MONTH, currentMonthStr, "system");
            saveSetting(KEY_LAST_ALERT_LEVEL, "100", "system");

            // Optional: Auto turn off Vendor AI at 100%
            if (isAutoDisableVendorAtBudget() && assistantSettingService != null) {
                if (assistantSettingService.isAssistantForVendorsEnabled()) {
                    assistantSettingService.setAssistantForVendorsEnabled(false, "System (AI Monthly Budget Exceeded)");
                    log.warn("Vendor AI automatically disabled due to 100% budget threshold reached. Staff features remain active.");
                }
            }

        } else if (ratio >= 0.80 && currentLevelRecorded < 80) {
            // 80% budget warning
            notifySuperAdmins(
                    "AI Monthly Budget Warning (80%)",
                    String.format("Estimated AI spend of Rp %,.0f has reached %.1f%% of the monthly budget (Rp %,.0f).",
                            spent, ratio * 100, budget),
                    NotificationType.WARNING
            );
            saveSetting(KEY_LAST_ALERT_MONTH, currentMonthStr, "system");
            saveSetting(KEY_LAST_ALERT_LEVEL, "80", "system");
        }
    }

    private void notifySuperAdmins(String title, String message, NotificationType type) {
        if (notificationService == null) return;
        List<AppUser> superAdmins = userRepository.findAll().stream()
                .filter(u -> u.getRoles() != null && u.getRoles().stream().anyMatch(r -> "ROLE_SUPER_ADMIN".equals(r.getName())))
                .toList();

        for (AppUser sa : superAdmins) {
            try {
                notificationService.createNotification(title, message, type, sa);
            } catch (Exception ex) {
                log.warn("Could not send budget notification to {}: {}", sa.getUsername(), ex.getMessage());
            }
        }
    }

    // ─── Dashboard Metrics DTO & Daily Aggregation ───────────────────────────

    public Map<String, Object> getDashboardData(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();
        YearMonth thisYm = YearMonth.now();

        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);

        LocalDateTime monthStart = thisYm.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = thisYm.atEndOfMonth().atTime(LocalTime.MAX);

        // Today's Stats
        long todayRequests = usageLogRepository.countByCreatedAtBetween(todayStart, todayEnd);
        Long todayTokens = usageLogRepository.sumTotalTokensBetween(todayStart, todayEnd);
        BigDecimal todayCostIdr = usageLogRepository.sumCostIdrBetween(todayStart, todayEnd);

        // Month's Stats
        long monthRequests = usageLogRepository.countByCreatedAtBetween(monthStart, monthEnd);
        Long monthTokens = usageLogRepository.sumTotalTokensBetween(monthStart, monthEnd);
        BigDecimal monthCostIdr = usageLogRepository.sumCostIdrBetween(monthStart, monthEnd);
        double budgetIdr = getMonthlyBudgetIdr();
        double budgetUsedPercent = budgetIdr > 0 ? (monthCostIdr.doubleValue() / budgetIdr) * 100.0 : 0.0;

        // Filter Window (defaults to last 30 days)
        LocalDate filterStart = startDate != null ? startDate : today.minusDays(29);
        LocalDate filterEnd = endDate != null ? endDate : today;
        LocalDateTime windowStart = filterStart.atStartOfDay();
        LocalDateTime windowEnd = filterEnd.atTime(LocalTime.MAX);

        // Failures count in filter window
        long filterTotalRequests = usageLogRepository.countByCreatedAtBetween(windowStart, windowEnd);
        long filterFailures = usageLogRepository.countBySuccessFalseAndCreatedAtBetween(windowStart, windowEnd);
        List<Object[]> failureBreakdown = usageLogRepository.aggregateFailuresByErrorTypeBetween(windowStart, windowEnd);

        // Breakdown by Feature
        List<Object[]> featureRows = usageLogRepository.aggregateByFeatureBetween(windowStart, windowEnd);
        List<Map<String, Object>> featureBreakdown = new ArrayList<>();
        for (Object[] r : featureRows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("feature", String.valueOf(r[0]));
            map.put("requests", ((Number) r[1]).longValue());
            map.put("inputTokens", ((Number) r[2]).longValue());
            map.put("outputTokens", ((Number) r[3]).longValue());
            map.put("costIdr", (BigDecimal) r[4]);
            featureBreakdown.add(map);
        }

        // Breakdown by User
        List<Object[]> userRows = usageLogRepository.aggregateByUserBetween(windowStart, windowEnd);
        List<Map<String, Object>> userBreakdown = new ArrayList<>();
        for (Object[] r : userRows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("username", String.valueOf(r[0]));
            map.put("role", String.valueOf(r[1]));
            map.put("requests", ((Number) r[2]).longValue());
            map.put("tokens", ((Number) r[3]).longValue());
            map.put("costIdr", (BigDecimal) r[4]);
            userBreakdown.add(map);
        }

        // Daily Chart Data for the last 30 days (or filter range)
        List<AiUsageLog> windowLogs = usageLogRepository.findAllByCreatedAtBetweenOrderByCreatedAtDesc(windowStart, windowEnd);
        Map<LocalDate, DailyStat> dailyStatMap = new TreeMap<>();
        LocalDate cur = filterStart;
        while (!cur.isAfter(filterEnd)) {
            dailyStatMap.put(cur, new DailyStat(cur));
            cur = cur.plusDays(1);
        }

        for (AiUsageLog logItem : windowLogs) {
            LocalDate d = logItem.getCreatedAt().toLocalDate();
            DailyStat stat = dailyStatMap.get(d);
            if (stat != null) {
                stat.requests++;
                if (logItem.isSuccess()) stat.successfulRequests++;
                else stat.failedRequests++;
                stat.tokens += logItem.getTotalTokens();
                stat.costIdr = stat.costIdr.add(logItem.getEstimatedCostIdr());
            }
        }

        List<DailyStat> chartData = new ArrayList<>(dailyStatMap.values());

        // Prepare response
        Map<String, Object> result = new HashMap<>();
        result.put("todayRequests", todayRequests);
        result.put("todayTokens", todayTokens != null ? todayTokens : 0L);
        result.put("todayCostIdr", todayCostIdr != null ? todayCostIdr : BigDecimal.ZERO);

        result.put("monthRequests", monthRequests);
        result.put("monthTokens", monthTokens != null ? monthTokens : 0L);
        result.put("monthCostIdr", monthCostIdr != null ? monthCostIdr : BigDecimal.ZERO);
        result.put("monthlyBudgetIdr", budgetIdr);
        result.put("budgetUsedPercent", Math.round(budgetUsedPercent * 10.0) / 10.0);
        result.put("isBudgetWarning80", budgetUsedPercent >= 80.0 && budgetUsedPercent < 100.0);
        result.put("isBudgetExceeded100", budgetUsedPercent >= 100.0);

        result.put("filterStart", filterStart.toString());
        result.put("filterEnd", filterEnd.toString());
        result.put("filterTotalRequests", filterTotalRequests);
        result.put("filterFailures", filterFailures);
        result.put("failureBreakdown", failureBreakdown);

        result.put("featureBreakdown", featureBreakdown);
        result.put("userBreakdown", userBreakdown);
        result.put("chartData", chartData);

        // Settings info
        result.put("priceInput1M", getPriceInputPerMillion());
        result.put("priceOutput1M", getPriceOutputPerMillion());
        result.put("exchangeRate", getExchangeRateUsdToIdr());
        result.put("autoDisableVendor", isAutoDisableVendorAtBudget());

        return result;
    }

    public static class DailyStat {
        public LocalDate date;
        public long requests = 0;
        public long successfulRequests = 0;
        public long failedRequests = 0;
        public long tokens = 0;
        public BigDecimal costIdr = BigDecimal.ZERO;

        public DailyStat(LocalDate date) {
            this.date = date;
        }

        public String getDateFormatted() {
            return date.format(DateTimeFormatter.ofPattern("dd MMM"));
        }
    }

    // ─── CSV Export (No Prompt/Answer Text) ───────────────────────────────────

    public void exportCsv(LocalDate startDate, LocalDate endDate, PrintWriter writer) {
        LocalDate startD = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate endD = endDate != null ? endDate : LocalDate.now();

        LocalDateTime start = startD.atStartOfDay();
        LocalDateTime end = endD.atTime(LocalTime.MAX);

        List<AiUsageLog> logs = usageLogRepository.findAllByCreatedAtBetweenOrderByCreatedAtDesc(start, end);

        // Header
        writer.println("Log_ID,Timestamp,Username,Role,Feature,Model,Input_Tokens,Output_Tokens,Total_Tokens,Duration_MS,Success,Error_Type,Estimated_Cost_USD,Estimated_Cost_IDR");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (AiUsageLog l : logs) {
            writer.printf("%d,%s,%s,%s,%s,%s,%d,%d,%d,%d,%s,%s,%.6f,%.2f%n",
                    l.getId(),
                    l.getCreatedAt() != null ? l.getCreatedAt().format(dtf) : "",
                    escapeCsv(l.getUsername()),
                    escapeCsv(l.getRole()),
                    escapeCsv(l.getFeature()),
                    escapeCsv(l.getModel()),
                    l.getInputTokens(),
                    l.getOutputTokens(),
                    l.getTotalTokens(),
                    l.getDurationMs(),
                    l.isSuccess() ? "TRUE" : "FALSE",
                    escapeCsv(l.getErrorType()),
                    l.getEstimatedCostUsd() != null ? l.getEstimatedCostUsd().doubleValue() : 0.0,
                    l.getEstimatedCostIdr() != null ? l.getEstimatedCostIdr().doubleValue() : 0.0
            );
        }
    }

    private String escapeCsv(String str) {
        if (str == null) return "";
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }

    // ─── System Settings Helpers ─────────────────────────────────────────────

    private double getDoubleSetting(String key, double defaultVal) {
        try {
            Optional<SystemSetting> opt = settingRepository.findById(key);
            if (opt.isPresent() && opt.get().getValue() != null) {
                return Double.parseDouble(opt.get().getValue().trim());
            }
        } catch (Exception ex) {
            log.warn("Could not read setting {}: {}", key, ex.getMessage());
        }
        return defaultVal;
    }

    private String getStringSetting(String key, String defaultVal) {
        try {
            Optional<SystemSetting> opt = settingRepository.findById(key);
            if (opt.isPresent() && opt.get().getValue() != null) {
                return opt.get().getValue().trim();
            }
        } catch (Exception ex) {
            log.warn("Could not read setting {}: {}", key, ex.getMessage());
        }
        return defaultVal;
    }

    private void saveSetting(String key, String value, String updatedBy) {
        SystemSetting s = settingRepository.findById(key)
                .orElseGet(() -> new SystemSetting(key, value, updatedBy));
        s.setValue(value);
        s.setUpdatedBy(updatedBy != null ? updatedBy : "system");
        s.setUpdatedAt(LocalDateTime.now());
        settingRepository.save(s);
    }
}
