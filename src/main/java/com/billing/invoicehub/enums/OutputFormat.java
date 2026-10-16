package com.billing.invoicehub.enums;

public enum OutputFormat {
    PDF,
    DOCX,
    BOTH;

    public static OutputFormat fromString(String value) {
        if (value == null || value.isBlank()) {
            return PDF;
        }
        try {
            return OutputFormat.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PDF;
        }
    }
}
