package com.billing.invoicehub.dto;

import java.util.List;

public class WeeklyTicketReportDto {
    private long totalCreated;
    private long pending;
    private long inProgress;
    private long paid;
    private long rejected;
    private long cancelled;

    private String totalCreatedChange;
    private String pendingChange;
    private String inProgressChange;
    private String paidChange;
    private String rejectedChange;
    private String cancelledChange;

    private List<String> days;
    private List<Long> dailyCounts;

    public long getTotalCreated() {
        return totalCreated;
    }

    public void setTotalCreated(long totalCreated) {
        this.totalCreated = totalCreated;
    }

    public long getPending() {
        return pending;
    }

    public void setPending(long pending) {
        this.pending = pending;
    }

    public long getInProgress() {
        return inProgress;
    }

    public void setInProgress(long inProgress) {
        this.inProgress = inProgress;
    }

    public long getPaid() {
        return paid;
    }

    public void setPaid(long paid) {
        this.paid = paid;
    }

    public long getRejected() {
        return rejected;
    }

    public void setRejected(long rejected) {
        this.rejected = rejected;
    }

    public long getCancelled() {
        return cancelled;
    }

    public void setCancelled(long cancelled) {
        this.cancelled = cancelled;
    }

    public String getTotalCreatedChange() {
        return totalCreatedChange;
    }

    public void setTotalCreatedChange(String totalCreatedChange) {
        this.totalCreatedChange = totalCreatedChange;
    }

    public String getPendingChange() {
        return pendingChange;
    }

    public void setPendingChange(String pendingChange) {
        this.pendingChange = pendingChange;
    }

    public String getInProgressChange() {
        return inProgressChange;
    }

    public void setInProgressChange(String inProgressChange) {
        this.inProgressChange = inProgressChange;
    }

    public String getPaidChange() {
        return paidChange;
    }

    public void setPaidChange(String paidChange) {
        this.paidChange = paidChange;
    }

    public String getRejectedChange() {
        return rejectedChange;
    }

    public void setRejectedChange(String rejectedChange) {
        this.rejectedChange = rejectedChange;
    }

    public String getCancelledChange() {
        return cancelledChange;
    }

    public void setCancelledChange(String cancelledChange) {
        this.cancelledChange = cancelledChange;
    }

    public List<String> getDays() {
        return days;
    }

    public void setDays(List<String> days) {
        this.days = days;
    }

    public List<Long> getDailyCounts() {
        return dailyCounts;
    }

    public void setDailyCounts(List<Long> dailyCounts) {
        this.dailyCounts = dailyCounts;
    }
}
