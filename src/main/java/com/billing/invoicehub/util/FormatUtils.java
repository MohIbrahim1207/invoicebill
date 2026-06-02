package com.billing.invoicehub.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Utility class for formatting commonly used values (currency, dates, etc.)
 */
public class FormatUtils {

    private static final Locale IDR_LOCALE = new Locale("id", "ID");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    private FormatUtils() {
        // Utility class, no instantiation
    }

    /**
     * Formats a BigDecimal amount as IDR currency
     * @param amount the amount to format
     * @return formatted string (e.g., "Rp 1.234.567,89")
     */
    public static String formatIDR(BigDecimal amount) {
        if (amount == null) {
            return "Rp 0";
        }
        try {
            NumberFormat nf = NumberFormat.getCurrencyInstance(IDR_LOCALE);
            return nf.format(amount);
        } catch (Exception e) {
            return "Rp " + amount.toPlainString();
        }
    }

    /**
     * Formats a LocalDateTime as a readable string
     * @param dateTime the datetime to format
     * @return formatted string (e.g., "Jan 15, 2026 02:30 PM")
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "-";
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    /**
     * Formats a LocalDateTime as date-only string
     * @param dateTime the datetime to format
     * @return formatted string (e.g., "Jan 15, 2026")
     */
    public static String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "-";
        }
        return dateTime.format(DATE_FORMATTER);
    }

    /**
     * Escapes HTML special characters to prevent XSS
     * @param value the value to escape
     * @return escaped string safe for HTML output
     */
    public static String escapeHtml(String value) {
        if (value == null) {
            return "-";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Truncates a string to a maximum length
     * @param value the string to truncate
     * @param maxLength the maximum length
     * @return truncated string with "..." appended if truncated
     */
    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return "-";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}

