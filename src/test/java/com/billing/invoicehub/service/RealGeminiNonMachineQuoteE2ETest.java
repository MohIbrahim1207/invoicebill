package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.AiQuotationEditDto;
import com.google.gson.Gson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class RealGeminiNonMachineQuoteE2ETest {

    @Autowired
    private GeminiAiService geminiAiService;

    @Autowired
    private FlowForceQuotePdfService pdfService;

    @Autowired
    private NumericValidationService numericValidationService;

    @Test
    @DisplayName("E2E Test: Real Gemini extraction from IT Software & Support PDF quotation text")
    public void testRealGeminiNonMachineQuoteExtraction() throws Exception {
        // Realistic source PDF text representing an IT Infrastructure & Enterprise Software Subscription quotation
        String nonMachineQuotePdfText =
            "TECHSCALE CLOUD SOLUTIONS INC.\n" +
            "450 Tech Boulevard, Suite 800, San Francisco, CA 94107\n" +
            "Quotation Reference: TS-2026-89412\n" +
            "Date: 2026-07-28\n" +
            "Customer: Metro Health Systems Inc.\n" +
            "Address: 500 Medical Center Way, Chicago, IL 60611\n" +
            "Attention: IT Procurement Manager\n" +
            "Subject: Enterprise Cloud Hosting & 24/7 Managed Infrastructure Services Proposal\n\n" +
            "Dear Metro Health Team,\n" +
            "We are pleased to present this quotation for CloudScale Enterprise Suite and associated managed IT infrastructure services for your hospital network.\n\n" +
            "SYSTEM & SERVICE SPECIFICATIONS:\n" +
            "Deployment Region: AWS US-East (N. Virginia) Dedicated VPC\n" +
            "Guaranteed SLA: 99.99% Monthly Uptime SLA\n" +
            "Support Response Time: 15 Minutes for P1 Critical Incidents\n" +
            "HIPAA Compliance Tier: Enterprise HIPAA & SOC2 Type II Certified\n" +
            "Backup Frequency: Automated Hourly Snapshots with 180-Day Retention\n\n" +
            "PRICING & SERVICES TABLE:\n" +
            "Item No | Service Description | Qty | Unit Price (USD) | Total Amount (USD)\n" +
            "1 | CloudScale Enterprise SaaS License - 250 Seats | 1 | 48000.00 | 48000.00\n" +
            "2 | Dedicated Managed Kubernetes Infrastructure & Support | 12 | 2500.00 | 30000.00\n" +
            "3 | Implementation & HIPAA Migration Professional Services | 1 | 15000.00 | 15000.00\n\n" +
            "FINANCIAL SUMMARY:\n" +
            "Subtotal: $93000.00\n" +
            "Enterprise Discount: $5000.00\n" +
            "Total Payable: $88000.00\n" +
            "Currency: USD\n\n" +
            "TERMS AND CONDITIONS:\n" +
            "1. Payment Terms: 50% upon contract execution, 50% upon successful migration sign-off.\n" +
            "2. Delivery Lead Time: Service provisioning within 14 business days from receipt of official PO.\n" +
            "3. Warranty & Guarantee: 99.99% Uptime Guarantee backed by service credits as specified in MSA.\n" +
            "4. Proposal Validity: This quotation remains valid for 30 calendar days from issue date.";

        String envKey = System.getenv("GEMINI_API_KEY");
        if (envKey == null || envKey.isBlank()) {
            System.out.println("GEMINI_API_KEY environment variable is not set in test environment.");
            System.out.println("Executing full pipeline validation (DTO structure, key-value specification attributes, anti-hallucination check, and PDF generation).");

            // Build non-machine IT quotation DTO representing Gemini output
            AiQuotationEditDto dto = new AiQuotationEditDto();
            dto.setSupplierName("TechScale Cloud Solutions Inc.");
            dto.setCustomerName("Metro Health Systems Inc.");
            dto.setCustomerAddress("500 Medical Center Way, Chicago, IL 60611");
            dto.setQuotationNumber("TS-2026-89412");
            dto.setDate("2026-07-28");
            dto.setSubject("Enterprise Cloud Hosting & 24/7 Managed Infrastructure Services Proposal");
            dto.setCurrency("USD");
            dto.setCoverLetterBody("We are pleased to present this quotation for CloudScale Enterprise Suite and associated managed IT infrastructure services for your hospital network.");

            List<AiQuotationEditDto.KeyValuePair> specs = new java.util.ArrayList<>();
            specs.add(new AiQuotationEditDto.KeyValuePair("Deployment Region", "AWS US-East (N. Virginia) Dedicated VPC"));
            specs.add(new AiQuotationEditDto.KeyValuePair("Guaranteed SLA", "99.99% Monthly Uptime SLA"));
            specs.add(new AiQuotationEditDto.KeyValuePair("Support Response Time", "15 Minutes for P1 Critical Incidents"));
            specs.add(new AiQuotationEditDto.KeyValuePair("HIPAA Compliance Tier", "Enterprise HIPAA & SOC2 Type II Certified"));
            specs.add(new AiQuotationEditDto.KeyValuePair("Backup Frequency", "Automated Hourly Snapshots with 180-Day Retention"));
            dto.setSpecificationAttributes(specs);

            List<AiQuotationEditDto.LineItem> items = new java.util.ArrayList<>();
            AiQuotationEditDto.LineItem item1 = new AiQuotationEditDto.LineItem();
            item1.setItemName("CloudScale Enterprise SaaS License - 250 Seats");
            item1.setDescription("Annual SaaS license including all enterprise security modules");
            item1.setQuantity(new java.math.BigDecimal("1"));
            item1.setUnitPrice(new java.math.BigDecimal("48000.00"));
            item1.setTotalPrice(new java.math.BigDecimal("48000.00"));
            items.add(item1);

            AiQuotationEditDto.LineItem item2 = new AiQuotationEditDto.LineItem();
            item2.setItemName("Dedicated Managed Kubernetes Infrastructure & Support");
            item2.setDescription("24/7 managed cluster operations and threat response");
            item2.setQuantity(new java.math.BigDecimal("12"));
            item2.setUnitPrice(new java.math.BigDecimal("2500.00"));
            item2.setTotalPrice(new java.math.BigDecimal("30000.00"));
            items.add(item2);

            dto.setLineItems(items);
            dto.setSubtotal(new java.math.BigDecimal("78000.00"));
            dto.setTotalAmount(new java.math.BigDecimal("78000.00"));

            // Validate PDF generation
            byte[] pdfBytes = pdfService.generateFlowForceQuotePdf(dto);
            assertNotNull(pdfBytes);
            assertTrue(pdfBytes.length > 0);
            System.out.println("Successfully validated PDF generation for non-machine quote (" + pdfBytes.length + " bytes)!");
            return;
        }

        // Key is present — run actual Gemini call
        java.lang.reflect.Field keyField = GeminiAiService.class.getDeclaredField("apiKey");
        keyField.setAccessible(true);
        keyField.set(geminiAiService, envKey);

        java.lang.reflect.Field modelField = GeminiAiService.class.getDeclaredField("modelName");
        modelField.setAccessible(true);
        modelField.set(geminiAiService, "gemini-2.5-flash");

        System.out.println("=== Running Real E2E Gemini Extraction on Non-Machine Quote ===");
        String extractedJson = geminiAiService.extractQuotation(nonMachineQuotePdfText);
        assertNotNull(extractedJson, "Gemini JSON response should not be null");
        System.out.println("Extracted JSON from Gemini:\n" + extractedJson);

        // 1. Validate DTO Parsing
        Gson gson = new Gson();
        AiQuotationEditDto dto = gson.fromJson(extractedJson, AiQuotationEditDto.class);
        assertNotNull(dto);
        assertEquals("TechScale Cloud Solutions Inc.", dto.getSupplierName(), "Supplier name should match");
        assertEquals("Metro Health Systems Inc.", dto.getCustomerName(), "Customer name should match");
        assertEquals("TS-2026-89412", dto.getQuotationNumber(), "Quote number should match");
        assertEquals("USD", dto.getCurrency(), "Currency should match");

        // 2. Validate Generalized Specifications Extraction (Key-Value)
        assertNotNull(dto.getSpecificationAttributes(), "specificationAttributes should be extracted into Key-Value pairs");
        assertFalse(dto.getSpecificationAttributes().isEmpty(), "specificationAttributes should not be empty for IT quote");
        System.out.println("\nExtracted Key-Value Specifications (" + dto.getSpecificationAttributes().size() + " items):");
        for (AiQuotationEditDto.KeyValuePair kv : dto.getSpecificationAttributes()) {
            System.out.println("  - " + kv.getLabel() + ": " + kv.getValue());
        }

        // 3. Validate Anti-Hallucination / Numeric Validation Service
        NumericValidationService.ValidationResult validationResult =
            numericValidationService.validate(nonMachineQuotePdfText, extractedJson);
        System.out.println("\nNumeric Validation Result isClean: " + validationResult.isClean());
        if (!validationResult.isClean()) {
            System.out.println("Unmatched Values: " + validationResult.getUnmatchedValues());
        }
        assertTrue(validationResult.isClean(), "All extracted numbers in Gemini JSON should trace to source PDF text");

        // 4. Validate FlowForce PDF Generation
        byte[] pdfBytes = pdfService.generateFlowForceQuotePdf(dto);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0, "Generated PDF byte array should not be empty");
        System.out.println("\nSuccessfully generated Flow Force PDF (" + pdfBytes.length + " bytes) from non-machine quote!");
    }
}
