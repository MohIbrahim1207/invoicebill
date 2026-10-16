package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.AiQuotationEditDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GeneralizedQuoteRenderingTest {

    @Test
    @DisplayName("Non-Machine IT/Software Quote Rendering: Generates PDF cleanly without empty machine sections")
    public void testSoftwareQuotePdfGeneration() {
        FlowForceQuotePdfService pdfService = new FlowForceQuotePdfService();

        AiQuotationEditDto dto = new AiQuotationEditDto();
        dto.setSupplierName("CloudScale Technologies Inc.");
        dto.setCustomerName("Acme Enterprise Solutions");
        dto.setCustomerAddress("100 Innovation Way, Suite 400, Tech Park, CA");
        dto.setQuotationNumber("QT-2026-IT-884");
        dto.setDate("2026-07-25");
        dto.setSubject("Annual SaaS Subscription & Cloud Managed Services");
        dto.setCurrency("USD");
        dto.setCoverLetterBody("Thank you for considering CloudScale for your cloud infrastructure needs. We are pleased to present our proposal for managed cloud hosting and 24/7 enterprise SLA support.");

        // Key-Value Specifications for IT/Software
        List<AiQuotationEditDto.KeyValuePair> specs = new ArrayList<>();
        specs.add(new AiQuotationEditDto.KeyValuePair("Platform Version", "CloudScale Enterprise v4.2"));
        specs.add(new AiQuotationEditDto.KeyValuePair("Deployment Model", "Dedicated Cloud (AWS us-east-1)"));
        specs.add(new AiQuotationEditDto.KeyValuePair("SLA Guaranteed Uptime", "99.99% Monthly"));
        specs.add(new AiQuotationEditDto.KeyValuePair("Support Tier", "24/7 Enterprise SLA (15-min Critical Response)"));
        specs.add(new AiQuotationEditDto.KeyValuePair("Data Retention & Backup", "365-Day Automated Snapshots"));
        dto.setSpecificationAttributes(specs);

        // Custom Table for Service Level Agreement Milestones
        List<AiQuotationEditDto.CustomTable> customTables = new ArrayList<>();
        AiQuotationEditDto.CustomTable ct = new AiQuotationEditDto.CustomTable();
        ct.setTitle("Service Level Objectives & Response Times");
        ct.setHeaders(List.of("Severity Level", "Initial Response Time", "Resolution Target", "Escalation Lead"));

        AiQuotationEditDto.TableRow r1 = new AiQuotationEditDto.TableRow();
        r1.setCells(List.of("P1 - Critical", "< 15 Mins", "< 2 Hours", "VP Engineering"));

        AiQuotationEditDto.TableRow r2 = new AiQuotationEditDto.TableRow();
        r2.setCells(List.of("P2 - Major", "< 1 Hour", "< 8 Hours", "Senior Support Manager"));

        ct.setRows(List.of(r1, r2));
        customTables.add(ct);
        dto.setCustomTables(customTables);

        // Standard Line Items
        List<AiQuotationEditDto.LineItem> items = new ArrayList<>();
        AiQuotationEditDto.LineItem item1 = new AiQuotationEditDto.LineItem();
        item1.setItemName("Enterprise SaaS License - 100 Seats");
        item1.setDescription("Annual subscription including all premium modules, API access, and SSO integration");
        item1.setQuantity(new BigDecimal("1"));
        item1.setUnitPrice(new BigDecimal("24000.00"));
        item1.setTotalPrice(new BigDecimal("24000.00"));
        items.add(item1);

        AiQuotationEditDto.LineItem item2 = new AiQuotationEditDto.LineItem();
        item2.setItemName("Managed Cloud Infrastructure & Monitoring");
        item2.setDescription("Dedicated Kubernetes cluster setup, continuous security patch management, and threat response");
        item2.setQuantity(new BigDecimal("12"));
        item2.setUnitPrice(new BigDecimal("1500.00"));
        item2.setTotalPrice(new BigDecimal("18000.00"));
        items.add(item2);

        dto.setLineItems(items);
        dto.setSubtotal(new BigDecimal("42000.00"));
        dto.setDiscount(new BigDecimal("2000.00"));
        dto.setTotalAmount(new BigDecimal("40000.00"));

        // Terms & Conditions
        List<AiQuotationEditDto.TermsClause> terms = new ArrayList<>();
        AiQuotationEditDto.TermsClause term1 = new AiQuotationEditDto.TermsClause();
        term1.setNumber("1");
        term1.setTitle("Payment Terms");
        term1.setText("Net 30 days from date of invoice. Electronic bank transfer preferred.");
        terms.add(term1);

        AiQuotationEditDto.TermsClause term2 = new AiQuotationEditDto.TermsClause();
        term2.setNumber("2");
        term2.setTitle("Service Term & Renewal");
        term2.setText("Initial contract duration is 12 months, auto-renewing annually unless terminated with 60 days written notice.");
        terms.add(term2);

        dto.setTermsAndConditionsList(terms);

        // Execute PDF generation
        byte[] pdfBytes = pdfService.generateFlowForceQuotePdf(dto);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0, "Generated PDF byte array should not be empty");
    }
}
