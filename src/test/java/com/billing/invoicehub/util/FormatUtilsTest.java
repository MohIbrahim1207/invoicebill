package com.billing.invoicehub.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatUtilsTest {

    @Test
    @DisplayName("Format full IDR currency correctly with Rp prefix and dot separator")
    void testFormatIDRFull() {
        assertEquals("Rp 0", FormatUtils.formatIDRFull(null));
        assertEquals("Rp 0", FormatUtils.formatIDRFull(BigDecimal.ZERO));
        assertEquals("Rp 9.845.000", FormatUtils.formatIDRFull(new BigDecimal("9845000")));
        assertEquals("Rp 500.000", FormatUtils.formatIDRFull(new BigDecimal("500000")));
        assertEquals("Rp 10.000.000", FormatUtils.formatIDRFull(new BigDecimal("10000000")));
    }

    @Test
    @DisplayName("Format compact IDR with rb (ribu), jt (juta), M (miliar), T (triliun)")
    void testFormatIDRCompact() {
        // Null & zero
        assertEquals("Rp 0", FormatUtils.formatIDRCompact(null));
        assertEquals("Rp 0", FormatUtils.formatIDRCompact(BigDecimal.ZERO));

        // Under 1,000
        assertEquals("Rp 500", FormatUtils.formatIDRCompact(new BigDecimal("500")));

        // Ribu (rb)
        assertEquals("Rp 50 rb", FormatUtils.formatIDRCompact(new BigDecimal("50000")));
        assertEquals("Rp 50,5 rb", FormatUtils.formatIDRCompact(new BigDecimal("50500")));

        // Juta (jt) - Prompt: "Note: 9.8 million = 'Rp 9,8 jt', NOT miliar."
        assertEquals("Rp 9,8 jt", FormatUtils.formatIDRCompact(new BigDecimal("9800000")));
        assertEquals("Rp 9,8 jt", FormatUtils.formatIDRCompact(new BigDecimal("9845000")));
        assertEquals("Rp 10 jt", FormatUtils.formatIDRCompact(new BigDecimal("10000000")));
        assertEquals("Rp 100 jt", FormatUtils.formatIDRCompact(new BigDecimal("100000000")));

        // Miliar (M)
        assertEquals("Rp 1 M", FormatUtils.formatIDRCompact(new BigDecimal("1000000000")));
        assertEquals("Rp 2,5 M", FormatUtils.formatIDRCompact(new BigDecimal("2500000000")));

        // Triliun (T)
        assertEquals("Rp 1 T", FormatUtils.formatIDRCompact(new BigDecimal("1000000000000")));
        assertEquals("Rp 1,2 T", FormatUtils.formatIDRCompact(new BigDecimal("1200000000000")));
    }
}

