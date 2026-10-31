package com.billing.invoicehub.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
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
     * Formats a BigDecimal amount as full IDR currency (e.g., "Rp 9.845.000")
     *
     * @param amount the amount to format
     * @return formatted string
     */
    public static String formatIDR(BigDecimal amount) {
        return formatCurrency(amount, "IDR");
    }

    /**
     * Formats a BigDecimal amount as full Indonesian Rupiah (e.g., "Rp 9.845.000")
     *
     * @param amount the amount to format
     * @return formatted string
     */
    public static String formatIDRFull(BigDecimal amount) {
        if (amount == null) {
            return "Rp 0";
        }
        NumberFormat nf = NumberFormat.getNumberInstance(IDR_LOCALE);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(0);
        return "Rp " + nf.format(amount);
    }

    /**
     * Formats a BigDecimal amount using compact Indonesian currency units:
     * rb = ribu (thousand), jt = juta (million), M = miliar (billion), T = triliun (trillion)
     * Example: 9.8 million -> "Rp 9,8 jt" (NOT miliar)
     *
     * @param amount the amount to format
     * @return compact formatted string
     */
    public static String formatIDRCompact(BigDecimal amount) {
        if (amount == null) {
            return "Rp 0";
        }
        double val = amount.doubleValue();
        if (val == 0) {
            return "Rp 0";
        }
        boolean negative = val < 0;
        double abs = Math.abs(val);

        String unit = "";
        double divisor = 1.0;

        if (abs >= 1_000_000_000_000.0) { // Triliun
            divisor = 1_000_000_000_000.0;
            unit = " T";
        } else if (abs >= 1_000_000_000.0) { // Miliar
            divisor = 1_000_000_000.0;
            unit = " M";
        } else if (abs >= 1_000_000.0) { // Juta
            divisor = 1_000_000.0;
            unit = " jt";
        } else if (abs >= 1_000.0) { // Ribu
            divisor = 1_000.0;
            unit = " rb";
        }

        double scaled = abs / divisor;
        DecimalFormat df = new DecimalFormat("#,##0.#", new DecimalFormatSymbols(IDR_LOCALE));
        String formattedNum = df.format(scaled);

        return (negative ? "-Rp " : "Rp ") + formattedNum + unit;
    }

    /**
     * Formats a BigDecimal amount with a specific currency
     * 
     * @param amount   the amount to format
     * @param currency the currency code (e.g., EUR, USD, IDR)
     * @return formatted string
     */
    public static String formatCurrency(BigDecimal amount, String currency) {
        if (amount == null) {
            return (currency != null && !currency.isBlank() ? currency.trim() : "IDR") + " 0";
        }
        String curr = (currency != null && !currency.isBlank()) ? currency.trim() : "IDR";
        NumberFormat nf;
        if ("IDR".equalsIgnoreCase(curr)) {
            nf = NumberFormat.getNumberInstance(IDR_LOCALE);
            nf.setMinimumFractionDigits(0);
            nf.setMaximumFractionDigits(0);
        } else {
            nf = NumberFormat.getNumberInstance(Locale.US);
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
        }
        return curr + " " + nf.format(amount);
    }

    /**
     * Formats a LocalDateTime as a readable string
     * 
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
     * 
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
     * 
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
     * 
     * @param value     the string to truncate
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
