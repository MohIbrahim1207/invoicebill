package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.AiQuotationEditDto;
import com.billing.invoicehub.entity.AiQuoteSession;
import com.billing.invoicehub.entity.AppUser;
import com.google.gson.Gson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AiQuoteSessionPdfGenerationTest {

    @Test
    @DisplayName("Verify PDF generation works cleanly for Session DTO with line items")
    public void testSessionPdfGeneration() throws Exception {
        FlowForceQuotePdfService pdfService = new FlowForceQuotePdfService();
        Gson gson = new Gson();

        AiQuotationEditDto dto = new AiQuotationEditDto();
        dto.setSupplierName("IDEX MPT, Inc.");
        dto.setCustomerName("Not Specified");
        dto.setQuotationNumber("QT-IDEX-9921");
        dto.setCurrency("USD");
        dto.setSubtotal(new BigDecimal("67500.00"));
        dto.setTotalAmount(new BigDecimal("67500.00"));

        List<AiQuotationEditDto.LineItem> lineItems = new ArrayList<>();
        AiQuotationEditDto.LineItem item = new AiQuotationEditDto.LineItem();
        item.setItemName("Microfluidix Homogenizer Model M110P");
        item.setDescription("High pressure lab homogenizer system with diamond interaction chamber");
        item.setQuantity(new BigDecimal("1"));
        item.setUnitPrice(new BigDecimal("67500.00"));
        item.setTotalPrice(new BigDecimal("67500.00"));
        lineItems.add(item);
        dto.setLineItems(lineItems);

        byte[] pdfBytes = pdfService.generateQuotationPdf(dto);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0, "PDF generation for IDEX MPT session should succeed");

        java.io.File targetFile = new java.io.File("target/generated_idex_mpt_session_quote.pdf");
        java.nio.file.Files.write(targetFile.toPath(), pdfBytes);
        assertTrue(targetFile.exists() && targetFile.length() > 0, "PDF file should be written to target/generated_idex_mpt_session_quote.pdf");
    }
}
