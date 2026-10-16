package com.billing.invoicehub.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Post-extraction anti-hallucination diff check.
 *
 * After Gemini returns structured JSON, this service compares numeric tokens
 * (prices, percentages, quantities) found in the Gemini output against those
 * present in the raw source PDF text. Any value present in the output but NOT
 * traceable to the source is flagged as a potential hallucination.
 */
@Service
public class NumericValidationService {

    private static final Logger logger = LoggerFactory.getLogger(NumericValidationService.class);

    /**
     * Matches numeric values including:
     * - Currency amounts: €1,234.56 $500 ₹1000 USD 1,234
     * - Percentages: 15% 30 %
     * - Plain numbers: 1234 1,234 1.234 1,234.56
     * Minimum 1 digit required. Single-digit standalone integers (1, 2, 3...) are
     * excluded
     * as they produce too many false positives from ordinal text.
     */
    private static final Pattern NUMERIC_TOKEN_PATTERN = Pattern.compile(
            "(?:(?:[€$₹]|USD|EUR|INR|IDR|GBP|AUD|SGD|MYR|THB|CNY|JPY|KRW)\\s*)?" +
                    "\\d{2,}(?:[,\\.]\\d+)*(?:\\s*%)?|" +
                    "\\d+(?:[,\\.]\\d{2,})(?:\\s*%)?",
            Pattern.CASE_INSENSITIVE);

    /** Result of a validation run. */
    public static class ValidationResult {
        private final boolean isClean;
        private final List<String> unmatchedValues;

        public ValidationResult(boolean isClean, List<String> unmatchedValues) {
            this.isClean = isClean;
            this.unmatchedValues = unmatchedValues;
        }

        public boolean isClean() {
            return isClean;
        }

        public List<String> getUnmatchedValues() {
            return unmatchedValues;
        }
    }

    /**
     * Extracts and normalizes all numeric tokens from the given text.
     */
    public Set<String> extractNumericTokens(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        Matcher m = NUMERIC_TOKEN_PATTERN.matcher(text);
        while (m.find()) {
            String raw = m.group().trim();
            String normalized = normalizeToken(raw);
            if (!normalized.isEmpty()) {
                tokens.add(normalized);
            }
        }
        return tokens;
    }

    /**
     * Normalizes a numeric token for comparison:
     * - Strips currency symbols and currency codes
     * - Removes internal spaces
     */
    /**
     * Normalizes a numeric token for comparison:
     * - Strips currency symbols and codes
     * - Removes commas (grouping separators)
     * - Standardizes decimal points and strips trailing zeros
     */
    private String normalizeToken(String token) {
        String clean = token
                .replaceAll("(?i)\\s*(USD|EUR|INR|IDR|GBP|AUD|SGD|MYR|THB|CNY|JPY|KRW)\\s*", "")
                .replaceAll("[€$₹]", "")
                .replaceAll("\\s+", "")
                .replaceAll(",", "") // Strip formatting commas
                .trim();

        // Remove trailing .00 or trailing decimals if it is a whole number
        // representation
        if (clean.contains(".")) {
            // Strip trailing zeros after decimal point
            clean = clean.replaceAll("0+$", "");
            // Strip the decimal point if it is at the end
            clean = clean.replaceAll("\\.$", "");
        }

        return clean;
    }

    /**
     * Finds numeric values in geminiJson that are not traceable to the sourceText.
     * Returns the list of unmatched value strings.
     */
    public List<String> findUnmatchedValues(String sourceText, String geminiJson) {
        if (sourceText == null || geminiJson == null) {
            return List.of();
        }

        // Extract tokens from source
        Set<String> sourceTokens = new LinkedHashSet<>();
        for (String raw : extractNumericTokens(sourceText)) {
            sourceTokens.add(normalizeToken(raw));
        }

        List<String> unmatched = new ArrayList<>();
        Matcher m = NUMERIC_TOKEN_PATTERN.matcher(geminiJson);
        while (m.find()) {
            String rawOutputToken = m.group().trim();
            String normalizedOutput = normalizeToken(rawOutputToken);
            if (normalizedOutput.isEmpty())
                continue;

            // Check if normalized token matches any normalized source token
            if (sourceTokens.contains(normalizedOutput))
                continue;

            // Secondary check: check if it matches by parsing numeric values
            boolean foundMatch = false;
            try {
                double outVal = Double.parseDouble(normalizedOutput);
                for (String srcTok : sourceTokens) {
                    try {
                        double srcVal = Double.parseDouble(srcTok);
                        if (Math.abs(outVal - srcVal) < 0.0001) {
                            foundMatch = true;
                            break;
                        }
                    } catch (NumberFormatException e) {
                        // ignore unparseable source tokens
                    }
                }
            } catch (NumberFormatException e) {
                // ignore unparseable output tokens
            }

            if (foundMatch)
                continue;

            // Secondary fallback: strip all non-digits and check if digitsOnly exists as a
            // standalone token
            String digitsOnly = normalizedOutput.replaceAll("[^\\d]", "");
            if (digitsOnly.length() >= 2) {
                boolean foundInDigits = false;
                for (String srcTok : sourceTokens) {
                    String srcDigits = srcTok.replaceAll("[^\\d]", "");
                    if (srcDigits.equals(digitsOnly)) {
                        foundInDigits = true;
                        break;
                    }
                }
                if (foundInDigits)
                    continue;
            }

            unmatched.add(rawOutputToken);
        }
        return unmatched;
    }

    /**
     * Runs a full validation comparing source PDF text against Gemini JSON output.
     *
     * @param sourceText raw text extracted from the source PDF
     * @param geminiJson the JSON string returned by Gemini
     * @return ValidationResult indicating whether the output is clean and listing
     *         any suspect values
     */
    public ValidationResult validate(String sourceText, String geminiJson) {
        List<String> unmatched = findUnmatchedValues(sourceText, geminiJson);
        boolean isClean = unmatched.isEmpty();

        if (!isClean) {
            logger.warn("Anti-hallucination check FAILED: {} value(s) in Gemini output not traceable to source PDF: {}",
                    unmatched.size(), unmatched);
        } else {
            logger.info(
                    "Anti-hallucination check PASSED: all numeric values in Gemini output are traceable to source PDF.");
        }

        return new ValidationResult(isClean, unmatched);
    }
}
