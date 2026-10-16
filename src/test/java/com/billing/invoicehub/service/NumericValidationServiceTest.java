package com.billing.invoicehub.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class NumericValidationServiceTest {

    private NumericValidationService numericValidationService;

    @BeforeEach
    public void setUp() {
        numericValidationService = new NumericValidationService();
    }

    @Test
    @DisplayName("Clean extraction: all numeric values in JSON exist in source text -> zero unmatched values")
    public void testCleanExtraction_AllValuesPresent() {
        String sourceText = "Quotation No: 10023. Total Amount: 41633.00. Tax: 6350.77. Subtotal: 35282.23.";
        String geminiJson = """
                {
                    "quotationNumber": "10023",
                    "subtotal": 35282.23,
                    "tax": 6350.77,
                    "totalAmount": 41633.00
                }
                """;

        NumericValidationService.ValidationResult result = numericValidationService.validate(sourceText, geminiJson);

        assertTrue(result.isClean(), "Validation should pass as clean");
        assertTrue(result.getUnmatchedValues().isEmpty(), "Unmatched values list should be empty");
    }

    @Test
    @DisplayName("Comma-formatted source ('41,633') matches plain decimal extracted ('41633.00') without false positive")
    public void testCommaFormattedSourceMatchesPlainDecimalExtracted() {
        String sourceText = "Grand Total: INR 41,633";
        String geminiJson = """
                {
                    "totalAmount": 41633.00
                }
                """;

        NumericValidationService.ValidationResult result = numericValidationService.validate(sourceText, geminiJson);

        assertTrue(result.isClean(),
                "Comma-formatted 41,633 in source must match 41633.00 in JSON without false positive");
        assertTrue(result.getUnmatchedValues().isEmpty(), "Should have 0 unmatched values");
    }

    @Test
    @DisplayName("Fabricated/altered numeric value in extracted JSON is correctly flagged as unmatched")
    public void testFabricatedNumericValueFlagged() {
        String sourceText = "Price: $1,500.00. Discount: $100.00. Final: $1,400.00.";
        // Extracted JSON contains hallucinated/altered total amount 9,999.00
        String geminiJson = """
                {
                    "subtotal": 1500.00,
                    "discount": 100.00,
                    "totalAmount": 9999.00
                }
                """;

        NumericValidationService.ValidationResult result = numericValidationService.validate(sourceText, geminiJson);

        assertFalse(result.isClean(), "Validation should fail for fabricated numeric value");
        assertFalse(result.getUnmatchedValues().isEmpty(), "Unmatched values list should contain the altered token");
        assertTrue(result.getUnmatchedValues().stream().anyMatch(v -> v.contains("9999") || v.contains("9,999")),
                "Should flag 9999 as unmatched value");
    }

    @Test
    @DisplayName("Null and blank input safety handling")
    public void testNullAndBlankInputHandling() {
        NumericValidationService.ValidationResult resultNull = numericValidationService.validate(null, null);
        assertTrue(resultNull.isClean(), "Null inputs should return clean result with empty unmatched list");
        assertTrue(resultNull.getUnmatchedValues().isEmpty());

        Set<String> tokensNull = numericValidationService.extractNumericTokens(null);
        assertTrue(tokensNull.isEmpty(), "Extracting tokens from null should return empty set");
    }
}
