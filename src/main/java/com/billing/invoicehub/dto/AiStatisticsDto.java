package com.billing.invoicehub.dto;

public class AiStatisticsDto {
    private long totalConversions;
    private long quotesGeneratedToday;
    private long pendingReviews;
    private double averageProcessingTimeSec;
    private double successRate;

    public long getTotalConversions() {
        return totalConversions;
    }

    public void setTotalConversions(long totalConversions) {
        this.totalConversions = totalConversions;
    }

    public long getQuotesGeneratedToday() {
        return quotesGeneratedToday;
    }

    public void setQuotesGeneratedToday(long quotesGeneratedToday) {
        this.quotesGeneratedToday = quotesGeneratedToday;
    }

    public long getPendingReviews() {
        return pendingReviews;
    }

    public void setPendingReviews(long pendingReviews) {
        this.pendingReviews = pendingReviews;
    }

    public double getAverageProcessingTimeSec() {
        return averageProcessingTimeSec;
    }

    public void setAverageProcessingTimeSec(double averageProcessingTimeSec) {
        this.averageProcessingTimeSec = averageProcessingTimeSec;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }
}
