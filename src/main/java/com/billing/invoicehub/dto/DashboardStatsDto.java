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
}

