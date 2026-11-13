package com.billing.invoicehub.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DashboardStatsDto {

    private long totalInvoices;
    private long pendingInvoices;
    private long approvedInvoices;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private String totalAmountFull = "Rp 0";
    private String totalAmountCompact = "Rp 0";

    // Monthly volume chart data (last 6 months)
    private List<String> monthlyLabels = new ArrayList<>();
    private List<Long> monthlyCounts = new ArrayList<>();
    private List<BigDecimal> monthlyAmounts = new ArrayList<>();
    private List<String> monthlyAmountsFormatted = new ArrayList<>();

    // Status donut chart data
    private List<String> statusLabels = new ArrayList<>();
    private List<Long> statusCounts = new ArrayList<>();
    private List<String> statusColors = new ArrayList<>();

    // 1.7.2 UI Overview Extensions
    private NeedsAttentionDto needsAttention = new NeedsAttentionDto();
    private List<StatusAmountDto> statusAmounts = new ArrayList<>();
    private List<TopVendorDto> topVendors = new ArrayList<>();
    private List<RecentActivityDto> recentActivities = new ArrayList<>();

    public long getTotalInvoices() {
        return totalInvoices;
    }

    public void setTotalInvoices(long totalInvoices) {
        this.totalInvoices = totalInvoices;
    }

    public long getPendingInvoices() {
        return pendingInvoices;
    }

    public void setPendingInvoices(long pendingInvoices) {
        this.pendingInvoices = pendingInvoices;
    }

    public long getApprovedInvoices() {
        return approvedInvoices;
    }

    public void setApprovedInvoices(long approvedInvoices) {
        this.approvedInvoices = approvedInvoices;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getTotalAmountFull() {
        return totalAmountFull;
    }

    public void setTotalAmountFull(String totalAmountFull) {
        this.totalAmountFull = totalAmountFull;
    }

    public String getTotalAmountCompact() {
        return totalAmountCompact;
    }

    public void setTotalAmountCompact(String totalAmountCompact) {
        this.totalAmountCompact = totalAmountCompact;
    }

    public List<String> getMonthlyLabels() {
        return monthlyLabels;
    }

    public void setMonthlyLabels(List<String> monthlyLabels) {
        this.monthlyLabels = monthlyLabels;
    }

    public List<Long> getMonthlyCounts() {
        return monthlyCounts;
    }

    public void setMonthlyCounts(List<Long> monthlyCounts) {
        this.monthlyCounts = monthlyCounts;
    }

    public List<BigDecimal> getMonthlyAmounts() {
        return monthlyAmounts;
    }

    public void setMonthlyAmounts(List<BigDecimal> monthlyAmounts) {
        this.monthlyAmounts = monthlyAmounts;
    }

    public List<String> getMonthlyAmountsFormatted() {
        return monthlyAmountsFormatted;
    }

    public void setMonthlyAmountsFormatted(List<String> monthlyAmountsFormatted) {
        this.monthlyAmountsFormatted = monthlyAmountsFormatted;
    }

    public List<String> getStatusLabels() {
        return statusLabels;
    }

    public void setStatusLabels(List<String> statusLabels) {
        this.statusLabels = statusLabels;
    }

    public List<Long> getStatusCounts() {
        return statusCounts;
    }

    public void setStatusCounts(List<Long> statusCounts) {
        this.statusCounts = statusCounts;
    }

    public List<String> getStatusColors() {
        return statusColors;
    }

    public void setStatusColors(List<String> statusColors) {
        this.statusColors = statusColors;
    }

    public NeedsAttentionDto getNeedsAttention() {
        return needsAttention;
    }

    public void setNeedsAttention(NeedsAttentionDto needsAttention) {
        this.needsAttention = needsAttention;
    }

    public List<StatusAmountDto> getStatusAmounts() {
        return statusAmounts;
    }

    public void setStatusAmounts(List<StatusAmountDto> statusAmounts) {
        this.statusAmounts = statusAmounts;
    }

    public List<TopVendorDto> getTopVendors() {
        return topVendors;
    }

    public void setTopVendors(List<TopVendorDto> topVendors) {
        this.topVendors = topVendors;
    }

    public List<RecentActivityDto> getRecentActivities() {
        return recentActivities;
    }

    public void setRecentActivities(List<RecentActivityDto> recentActivities) {
        this.recentActivities = recentActivities;
    }

    // ─── Nested DTOs for 1.7.2 Dashboard Overview ─────────────────────────────

    public static class NeedsAttentionDto {
        private int pendingThresholdDays = 7;
        private long pendingOverdueCount;
        private String pendingLink = "/admin/tickets?status=OPEN";
        private long unsyncedCount;
        private String unsyncedLink = "/admin/tickets?unsynced=true";
        private long revisedCount;
        private String revisedLink = "/admin/tickets?status=REVISE";
        private long unverifiedVendorsCount;
        private String unverifiedVendorsLink = "/admin/vendors";

        public boolean isHasItems() {
            return pendingOverdueCount > 0 || unsyncedCount > 0 || revisedCount > 0 || unverifiedVendorsCount > 0;
        }

        public int getPendingThresholdDays() {
            return pendingThresholdDays;
        }

        public void setPendingThresholdDays(int pendingThresholdDays) {
            this.pendingThresholdDays = pendingThresholdDays;
        }

        public long getPendingOverdueCount() {
            return pendingOverdueCount;
        }

        public void setPendingOverdueCount(long pendingOverdueCount) {
            this.pendingOverdueCount = pendingOverdueCount;
        }

        public String getPendingLink() {
            return pendingLink;
        }

        public void setPendingLink(String pendingLink) {
            this.pendingLink = pendingLink;
        }

        public long getUnsyncedCount() {
            return unsyncedCount;
        }

        public void setUnsyncedCount(long unsyncedCount) {
            this.unsyncedCount = unsyncedCount;
        }

        public String getUnsyncedLink() {
            return unsyncedLink;
        }

        public void setUnsyncedLink(String unsyncedLink) {
            this.unsyncedLink = unsyncedLink;
        }

        public long getRevisedCount() {
            return revisedCount;
        }

        public void setRevisedCount(long revisedCount) {
            this.revisedCount = revisedCount;
        }

        public String getRevisedLink() {
            return revisedLink;
        }

        public void setRevisedLink(String revisedLink) {
            this.revisedLink = revisedLink;
        }

        public long getUnverifiedVendorsCount() {
            return unverifiedVendorsCount;
        }

        public void setUnverifiedVendorsCount(long unverifiedVendorsCount) {
            this.unverifiedVendorsCount = unverifiedVendorsCount;
        }

        public String getUnverifiedVendorsLink() {
            return unverifiedVendorsLink;
        }

        public void setUnverifiedVendorsLink(String unverifiedVendorsLink) {
            this.unverifiedVendorsLink = unverifiedVendorsLink;
        }
    }

    public static class StatusAmountDto {
        private String status;
        private String label;
        private String color;
        private String pillClass;
        private long count;
        private BigDecimal amount = BigDecimal.ZERO;
        private String formattedAmount = "Rp 0";
        private double percentage = 0.0;
        private String formattedPercentage = "0.0%";

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }

        public String getPillClass() {
            return pillClass;
        }

        public void setPillClass(String pillClass) {
            this.pillClass = pillClass;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getFormattedAmount() {
            return formattedAmount;
        }

        public void setFormattedAmount(String formattedAmount) {
            this.formattedAmount = formattedAmount;
        }

        public double getPercentage() {
            return percentage;
        }

        public void setPercentage(double percentage) {
            this.percentage = percentage;
        }

        public String getFormattedPercentage() {
            return formattedPercentage;
        }

        public void setFormattedPercentage(String formattedPercentage) {
            this.formattedPercentage = formattedPercentage;
        }
    }

    public static class TopVendorDto {
        private int rank;
        private Long vendorId;
        private String vendorName;
        private String vendorCode;
        private long invoiceCount;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private String formattedAmount = "Rp 0";

        public int getRank() {
            return rank;
        }

        public void setRank(int rank) {
            this.rank = rank;
        }

        public Long getVendorId() {
            return vendorId;
        }

        public void setVendorId(Long vendorId) {
            this.vendorId = vendorId;
        }

        public String getVendorName() {
            return vendorName;
        }

        public void setVendorName(String vendorName) {
            this.vendorName = vendorName;
        }

        public String getVendorCode() {
            return vendorCode;
        }

        public void setVendorCode(String vendorCode) {
            this.vendorCode = vendorCode;
        }

        public long getInvoiceCount() {
            return invoiceCount;
        }

        public void setInvoiceCount(long invoiceCount) {
            this.invoiceCount = invoiceCount;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        public void setTotalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        }

        public String getFormattedAmount() {
            return formattedAmount;
        }

        public void setFormattedAmount(String formattedAmount) {
            this.formattedAmount = formattedAmount;
        }
    }

    public static class RecentActivityDto {
        private Long id;
        private String action;
        private String username;
        private String role;
        private String entityType;
        private Long entityId;
        private String description;
        private String targetUrl;
        private java.time.LocalDateTime timestamp;
        private String formattedTime;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getEntityType() {
            return entityType;
        }

        public void setEntityType(String entityType) {
            this.entityType = entityType;
        }

        public Long getEntityId() {
            return entityId;
        }

        public void setEntityId(Long entityId) {
            this.entityId = entityId;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getTargetUrl() {
            return targetUrl;
        }

        public void setTargetUrl(String targetUrl) {
            this.targetUrl = targetUrl;
        }

        public java.time.LocalDateTime getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(java.time.LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }

        public String getFormattedTime() {
            return formattedTime;
        }

        public void setFormattedTime(String formattedTime) {
            this.formattedTime = formattedTime;
        }
    }
}

