package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.AiQuotationEditDto;
import com.billing.invoicehub.enums.OutputFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class FlowForceQuoteDocxServiceTest {

    @Test
    @DisplayName("Verify native Word DOCX generation creates valid editable document bytes")
    public void testDocxGeneration() {
        FlowForceQuoteDocxService service = new FlowForceQuoteDocxService();

        AiQuotationEditDto dto = new AiQuotationEditDto();
        dto.setSupplierName("IDEX MPT, Inc.");
        dto.setCustomerName("Test Customer Ltd");
        dto.setQuotationNumber("QT-TEST-2026");
        dto.setCurrency("USD");
        dto.setSubtotal(new BigDecimal("50000.00"));
        dto.setTotalAmount(new BigDecimal("50000.00"));

        dto.setLineItems(new ArrayList<>());
        AiQuotationEditDto.LineItem item = new AiQuotationEditDto.LineItem();
        item.setItemName("Homogenizer Model M110P");
        item.setDescription("High pressure homogenizer system");
        item.setQuantity(new BigDecimal("1"));
        item.setUnitPrice(new BigDecimal("50000.00"));
        item.setTotalPrice(new BigDecimal("50000.00"));
        dto.getLineItems().add(item);

        byte[] docxBytes = service.generateFlowForceQuoteDocx(dto);

        assertNotNull(docxBytes, "Generated DOCX bytes must not be null");
        assertTrue(docxBytes.length > 0, "Generated DOCX bytes must have non-zero size");
    }

    @Test
    @DisplayName("Verify OutputFormat.fromString parser")
    public void testOutputFormatEnum() {
        assertEquals(OutputFormat.PDF, OutputFormat.fromString("pdf"));
        assertEquals(OutputFormat.DOCX, OutputFormat.fromString("docx"));
        assertEquals(OutputFormat.BOTH, OutputFormat.fromString("both"));
        assertEquals(OutputFormat.PDF, OutputFormat.fromString(null));
        assertEquals(OutputFormat.PDF, OutputFormat.fromString("invalid"));
    }
}
