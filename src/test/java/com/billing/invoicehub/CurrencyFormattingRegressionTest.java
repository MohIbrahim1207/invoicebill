package com.billing.invoicehub;

import com.billing.invoicehub.util.FormatUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class CurrencyFormattingRegressionTest {

    @Test
    @DisplayName("IDR: Exact ticket 41 value (11243190.00 -> IDR 11.243.190) bank-account format without decimals")
    void testIdrTicket41AmountFormatting() {
        BigDecimal amount = new BigDecimal("11243190.00");
        String formatted = FormatUtils.formatCurrency(amount, "IDR");
        assertEquals("IDR 11.243.190", formatted,
                "IDR 11243190.00 must format as bank-account whole number with dot thousands separator as 'IDR 11.243.190'");

        String viaFormatIdr = FormatUtils.formatIDR(amount);
        assertEquals("IDR 11.243.190", viaFormatIdr);
    }

    @Test
    @DisplayName("IDR: Example amount (888000.00 -> IDR 888.000)")
    void testIdrExampleAmount() {
        BigDecimal amount = new BigDecimal("888000.00");
        String formatted = FormatUtils.formatCurrency(amount, "IDR");
        assertEquals("IDR 888.000", formatted);
    }

    @Test
    @DisplayName("IDR: Zero and null handling formats as IDR 0 without decimals")
    void testIdrZeroAndNullHandling() {
        assertEquals("IDR 0", FormatUtils.formatCurrency(BigDecimal.ZERO, "IDR"));
        assertEquals("IDR 0", FormatUtils.formatCurrency(null, "IDR"));
        assertEquals("IDR 0", FormatUtils.formatIDR(null));
    }

    @Test
    @DisplayName("IDR: Extremely large amount (1 billion IDR)")
    void testIdrBillionAmount() {
        BigDecimal billion = new BigDecimal("1000000000.00");
        assertEquals("IDR 1.000.000.000", FormatUtils.formatCurrency(billion, "IDR"));
    }

    @Test
    @DisplayName("EUR: Retains 2 decimal places (11243190.00 -> EUR 11,243,190.00)")
    void testEurLargeAmountFormatting() {
        BigDecimal amount = new BigDecimal("11243190.00");
        String formatted = FormatUtils.formatCurrency(amount, "EUR");
        assertEquals("EUR 11,243,190.00", formatted);

        BigDecimal fraction = new BigDecimal("1234.56");
        assertEquals("EUR 1,234.56", FormatUtils.formatCurrency(fraction, "EUR"));
        assertEquals("EUR 0", FormatUtils.formatCurrency(null, "EUR"));
    }

    @Test
    @DisplayName("USD: Standard US formatting retains 2 decimal places (11243190.00 -> USD 11,243,190.00)")
    void testUsdAmountFormatting() {
        BigDecimal amount = new BigDecimal("11243190.00");
        assertEquals("USD 11,243,190.00", FormatUtils.formatCurrency(amount, "USD"));

        BigDecimal fraction = new BigDecimal("1234.56");
        assertEquals("USD 1,234.56", FormatUtils.formatCurrency(fraction, "USD"));
        assertEquals("USD 0", FormatUtils.formatCurrency(null, "USD"));
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
