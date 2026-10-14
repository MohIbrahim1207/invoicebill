package com.billing.invoicehub;

import com.billing.invoicehub.util.FormatUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class CurrencyFormattingRegressionTest {

    @Test
    @DisplayName("IDR: Format large amount with 2 decimal places using Indonesian number format (11243190.00 -> IDR 11.243.190,00)")
    void testIdrLargeAmountFormatting() {
        BigDecimal amount = new BigDecimal("11243190.00");
        String formatted = FormatUtils.formatCurrency(amount, "IDR");
        assertEquals("IDR 11.243.190,00", formatted,
                "IDR 11243190.00 must format with dot thousands separator and comma decimal separator as 'IDR 11.243.190,00'");

        String viaFormatIdr = FormatUtils.formatIDR(amount);
        assertEquals("IDR 11.243.190,00", viaFormatIdr);
    }

    @Test
    @DisplayName("IDR: Preserves fractional decimals (11243190.50 -> IDR 11.243.190,50)")
    void testIdrFractionalAmount() {
        BigDecimal amount = new BigDecimal("11243190.50");
        String formatted = FormatUtils.formatCurrency(amount, "IDR");
        assertEquals("IDR 11.243.190,50", formatted);
    }

    @Test
    @DisplayName("IDR: Zero and null handling preserves 2 decimal digits")
    void testIdrZeroAndNullHandling() {
        assertEquals("IDR 0,00", FormatUtils.formatCurrency(BigDecimal.ZERO, "IDR"));
        assertEquals("IDR 0,00", FormatUtils.formatCurrency(null, "IDR"));
        assertEquals("IDR 0,00", FormatUtils.formatIDR(null));
    }

    @Test
    @DisplayName("IDR: Extremely large amount (1 billion IDR)")
    void testIdrBillionAmount() {
        BigDecimal billion = new BigDecimal("1000000000.00");
        assertEquals("IDR 1.000.000.000,00", FormatUtils.formatCurrency(billion, "IDR"));
    }

    @Test
    @DisplayName("EUR: European locale formatting (11243190.00 -> EUR 11.243.190,00)")
    void testEurLargeAmountFormatting() {
        BigDecimal amount = new BigDecimal("11243190.00");
        String formatted = FormatUtils.formatCurrency(amount, "EUR");
        assertEquals("EUR 11.243.190,00", formatted,
                "EUR amounts must use European number format with dot grouping and comma decimals");
    }

    @Test
    @DisplayName("EUR: Standard fractional values (1234.56 -> EUR 1.234,56)")
    void testEurFractionalValues() {
        BigDecimal amount = new BigDecimal("1234.56");
        assertEquals("EUR 1.234,56", FormatUtils.formatCurrency(amount, "EUR"));

        BigDecimal zero = new BigDecimal("0.00");
        assertEquals("EUR 0,00", FormatUtils.formatCurrency(zero, "EUR"));
        assertEquals("EUR 0,00", FormatUtils.formatCurrency(null, "EUR"));
    }

    @Test
    @DisplayName("USD: Standard US locale formatting (11243190.00 -> USD 11,243,190.00)")
    void testUsdAmountFormatting() {
        BigDecimal amount = new BigDecimal("11243190.00");
        assertEquals("USD 11,243,190.00", FormatUtils.formatCurrency(amount, "USD"));

        BigDecimal fraction = new BigDecimal("1234.56");
        assertEquals("USD 1,234.56", FormatUtils.formatCurrency(fraction, "USD"));
    }

    @Test
    @DisplayName("Preserve decimal precision without modifying original stored BigDecimal")
    void testStoredBigDecimalPreserved() {
        BigDecimal original = new BigDecimal("11243190.00");
        BigDecimal copy = new BigDecimal("11243190.00");

        String formatted = FormatUtils.formatCurrency(original, "IDR");
        assertNotNull(formatted);

        assertEquals(0, original.compareTo(copy), "Stored BigDecimal numerical value must remain unchanged");
        assertEquals(copy.scale(), original.scale(), "Stored BigDecimal scale must remain unchanged");
        assertEquals("11243190.00", original.toPlainString(), "Plain string representation must remain unchanged");
    }
}
