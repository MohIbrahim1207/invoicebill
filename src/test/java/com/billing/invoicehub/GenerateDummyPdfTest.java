package com.billing.invoicehub;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;

import java.io.InputStream;
import com.lowagie.text.Image;
import org.springframework.core.io.ClassPathResource;
import com.billing.invoicehub.dto.AiQuotationEditDto;
import com.billing.invoicehub.service.FlowForceQuotePdfService;
import java.math.BigDecimal;

public class GenerateDummyPdfTest {

    @Test
    public void testCheckBrandingImages() {
        try {
            ClassPathResource headerRes = new ClassPathResource("static/images/logo.png");
            ClassPathResource signRes = new ClassPathResource("static/images/Umapathi-signature.png");
            
            try (InputStream is = headerRes.getInputStream()) {
                byte[] bytes = is.readAllBytes();
                Image img = Image.getInstance(bytes);
                System.out.println("LOGO_IMAGE_INFO: width=" + img.getWidth() + ", height=" + img.getHeight());
            }
            
            try (InputStream is = signRes.getInputStream()) {
                byte[] bytes = is.readAllBytes();
                Image img = Image.getInstance(bytes);
                System.out.println("SIGN_IMAGE_INFO: width=" + img.getWidth() + ", height=" + img.getHeight());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testGeneratePdf() {
        try {
            File uploadsDir = new File("c:/Users/NEW/Invoicehub/uploads");
            if (!uploadsDir.exists()) {
                uploadsDir.mkdirs();
            }
            File pdfFile = new File(uploadsDir, "dummy_supplier_quote.pdf");
            Document document = new Document();
            PdfWriter.getInstance(document, new FileOutputStream(pdfFile));
            document.open();
            document.add(new Paragraph("Supplier: Acme Industrial Corp"));
            document.add(new Paragraph("Quotation Number: ACME-2026-99"));
            document.add(new Paragraph("Date: 2026-07-06"));
            document.add(new Paragraph("To: Flow Force"));
            document.add(new Paragraph("Machine: Industrial Compressor"));
            document.add(new Paragraph("Model: AC-500"));
            document.add(new Paragraph("Capacity: 500 CFM"));
            document.add(new Paragraph("Technical Specifications:"));
            document.add(new Paragraph("- High efficiency rotary screw air end"));
            document.add(new Paragraph("- Premium Siemens electrical control panel"));
            document.add(new Paragraph("- Heavy duty noise reduction enclosure"));
            document.add(new Paragraph("Line Items:"));
            document.add(new Paragraph("1. Main Compressor Unit: Model AC-500, Qty: 1, Unit Price: 15000.00, Total: 15000.00"));
            document.add(new Paragraph("2. Extra Air Filter Elements: Qty: 5, Unit Price: 100.00, Total: 500.00"));
            document.add(new Paragraph("Subtotal: 15500.00"));
            document.add(new Paragraph("Discount: 500.00"));
            document.add(new Paragraph("Tax: 1500.00"));
            document.add(new Paragraph("Total Amount: 16500.00"));
            document.add(new Paragraph("Warranty: 24 months"));
            document.add(new Paragraph("Delivery Time: 4 weeks"));
            document.add(new Paragraph("Payment Terms: 30% advance, 70% on delivery"));
            document.add(new Paragraph("Validity: 60 days"));
            document.add(new Paragraph("Notes: Standard packaging included."));
            document.close();
            System.out.println("Dummy PDF generated successfully at: " + pdfFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testGenerateBrandedPdf() {
        try {
            AiQuotationEditDto dto = new AiQuotationEditDto();
            dto.setCustomerName("Global Manufacturing Ltd");
            dto.setAttention("Procurement Department");
            dto.setDate("2026-07-07");
            dto.setReference("FF/QUOTE/2026-102");
            dto.setSubject("Supply of High Efficiency Industrial Compressor Unit");
            
            dto.setMachineName("Industrial Rotary Screw Air Compressor");
            dto.setMachineModel("AC-500 Premium");
            dto.setCapacity("500 CFM @ 8.5 Bar");
            dto.setTechnicalSpecifications("1. High efficiency rotary screw air end\n2. Siemens Premium PLC Electrical Control Panel\n3. Heavy duty noise reduction enclosure (<72 dBA)\n4. Integrated refrigerated air dryer");
            
            dto.setCurrency("USD");
            dto.setSubtotal(new BigDecimal("15500.00"));
            dto.setDiscount(new BigDecimal("500.00"));
            dto.setTax(new BigDecimal("1500.00"));
            dto.setTotalAmount(new BigDecimal("16500.00"));
            
            dto.setDeliveryTime("4 weeks from purchase order");
            dto.setWarranty("24 months comprehensive warranty");
            dto.setPaymentTerms("30% advance deposit, 70% against delivery");
            dto.setValidity("60 Days");
            dto.setNotes("1. Prices are EXW sectoral warehouse.\n2. Installation assistance is included.\n3. Standard sea-worthy wooden packing included.");

            java.util.List<AiQuotationEditDto.LineItem> items = new java.util.ArrayList<>();
            AiQuotationEditDto.LineItem item1 = new AiQuotationEditDto.LineItem();
            item1.setItemName("AC-500 Compressor");
            item1.setDescription("Main Compressor Unit with Siemens VFD");
            item1.setQuantity(new BigDecimal("1.00"));
            item1.setUnitPrice(new BigDecimal("15000.00"));
            item1.setTotalPrice(new BigDecimal("15000.00"));
            items.add(item1);

            AiQuotationEditDto.LineItem item2 = new AiQuotationEditDto.LineItem();
            item2.setItemName("Service Filter Kit");
            item2.setDescription("Consumable filters for first 2000 hours run");
            item2.setQuantity(new BigDecimal("5.00"));
            item2.setUnitPrice(new BigDecimal("100.00"));
            item2.setTotalPrice(new BigDecimal("500.00"));
            items.add(item2);
            dto.setLineItems(items);

            java.util.List<AiQuotationEditDto.OptionalItem> optionals = new java.util.ArrayList<>();
            AiQuotationEditDto.OptionalItem opt1 = new AiQuotationEditDto.OptionalItem();
            opt1.setItemName("Extended Warranty Plan");
            opt1.setDescription("Additional 12 months comprehensive warranty coverage");
            opt1.setPrice(new BigDecimal("1200.00"));
            optionals.add(opt1);
            dto.setOptionalItems(optionals);

            FlowForceQuotePdfService pdfService = new FlowForceQuotePdfService();
            
            // Set paths using reflection to simulate Spring injection in unit test environment
            java.lang.reflect.Field headerField = FlowForceQuotePdfService.class.getDeclaredField("logoImagePath");
            headerField.setAccessible(true);
            headerField.set(pdfService, "static/images/logo.png");

            java.lang.reflect.Field sigField = FlowForceQuotePdfService.class.getDeclaredField("signatureImagePath");
            sigField.setAccessible(true);
            sigField.set(pdfService, "static/images/Umapathi-signature.png");

            byte[] pdfBytes = pdfService.generateFlowForceQuotePdf(dto);
            
            File uploadsDir = new File("c:/Users/NEW/Invoicehub/uploads");
            if (!uploadsDir.exists()) {
                uploadsDir.mkdirs();
            }
            File pdfFile = new File(uploadsDir, "sample_flowforce_quotation.pdf");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(pdfFile)) {
                fos.write(pdfBytes);
            }
            System.out.println("Branded Flow Force PDF generated successfully at: " + pdfFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testGenerateFromRealBosPdf() {
        try {
            // 1. Read bos_mg2_800b.pdf bytes
            File uploadsDir = new File("c:/Users/NEW/Invoicehub/uploads");
            File sourcePdf = new File(uploadsDir, "bos_mg2_800b.pdf");
            if (!sourcePdf.exists()) {
                System.out.println("Source PDF not found, skipping real extraction test.");
                return;
            }
            byte[] fileBytes = java.nio.file.Files.readAllBytes(sourcePdf.toPath());

            // 2. Extract Text
            com.billing.invoicehub.service.PdfExtractionService pdfExtractionService = new com.billing.invoicehub.service.PdfExtractionService();
            String text = pdfExtractionService.extractText(fileBytes);
            System.out.println("EXTRACTED TEXT LENGTH: " + text.length());

            // 3. Extract Image
            byte[] imgBytes = pdfExtractionService.extractProductImage(fileBytes);
            String base64Image = null;
            if (imgBytes != null) {
                base64Image = java.util.Base64.getEncoder().encodeToString(imgBytes);
                System.out.println("EXTRACTED PRODUCT IMAGE BYTES: " + imgBytes.length);
            } else {
                System.out.println("NO PRODUCT IMAGE EXTRACTED!");
            }

            // 4. Extract structured JSON via Gemini
            String envKey = System.getenv("GEMINI_API_KEY");
            if (envKey == null || envKey.isBlank()) {
                System.out.println("No Gemini API key in environment, running simulated DTO with extracted image.");
                runSimulatedBosPdfGeneration(base64Image);
                return;
            }

            com.billing.invoicehub.service.GeminiAiService aiService = new com.billing.invoicehub.service.GeminiAiService();
            java.lang.reflect.Field keyField = com.billing.invoicehub.service.GeminiAiService.class.getDeclaredField("apiKey");
            keyField.setAccessible(true);
            keyField.set(aiService, envKey);

            java.lang.reflect.Field modelField = com.billing.invoicehub.service.GeminiAiService.class.getDeclaredField("modelName");
            modelField.setAccessible(true);
            modelField.set(aiService, "gemini-2.5-flash");

            String json = aiService.extractQuotation(text);
            System.out.println("GEMINI JSON RESPONSE: " + json);

            com.google.gson.Gson gson = new com.google.gson.Gson();
            AiQuotationEditDto dto = gson.fromJson(json, AiQuotationEditDto.class);
            dto.setProductImageBase64(base64Image);

            // 5. Generate Flow Force PDF
            FlowForceQuotePdfService pdfService = new FlowForceQuotePdfService();
            java.lang.reflect.Field logoField = FlowForceQuotePdfService.class.getDeclaredField("logoImagePath");
            logoField.setAccessible(true);
            logoField.set(pdfService, "static/images/logo.png");

            java.lang.reflect.Field sigField = FlowForceQuotePdfService.class.getDeclaredField("signatureImagePath");
            sigField.setAccessible(true);
            sigField.set(pdfService, "static/images/Umapathi-signature.png");

            byte[] pdfBytes = pdfService.generateFlowForceQuotePdf(dto);
            File outputFile = new File(uploadsDir, "FF_BOS_MG2_800B_Generated.pdf");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile)) {
                fos.write(pdfBytes);
            }
            System.out.println("Real BOS PDF generated successfully at: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void runSimulatedBosPdfGeneration(String base64Image) throws Exception {
        AiQuotationEditDto dto = new AiQuotationEditDto();
        dto.setCustomerName("PT. Global Food Industri");
        dto.setAttention("Mr. Budi");
        dto.setDate("2026-07-08");
        dto.setReference("FF/QUOTE/2026-999");
        dto.setSubject("BOS MG2-800B Homogeniser Quotation");
        dto.setMachineName("BOS high pressure homogeniser");
        dto.setMachineModel("MG2-800B");
        dto.setCapacity("80 l/h at 800 bar");
        dto.setProduct("Various products\nLab Homogeniser / Research");
        dto.setOperatingTemperature("85°C max");
        dto.setOperatingPressure("800 bar max. Total pressure");
        dto.setRequiredFeedPressure("2 bar + 0,05 bar per % solid content");
        dto.setPlungerDiameter("10 mm – 3 pieces");
        dto.setPowerConsumption("1,1 – 2,0 kW");
        dto.setEccentricShaftSpeed("99 – 199 rpm");
        dto.setProductImageBase64(base64Image);

        // Add line items
        java.util.List<AiQuotationEditDto.LineItem> items = new java.util.ArrayList<>();
        AiQuotationEditDto.LineItem item1 = new AiQuotationEditDto.LineItem();
        item1.setItemName("BOS MG2-800B Homogeniser");
        item1.setDescription("Cylinder block made of 1.4418 stainless steel");
        item1.setTotalPrice(BigDecimal.ZERO); // Included
        items.add(item1);
        
        AiQuotationEditDto.LineItem item2 = new AiQuotationEditDto.LineItem();
        item2.setItemName("Duplex Steel Option");
        item2.setDescription("Cylinder block made of Duplex grade stainless steel");
        item2.setTotalPrice(new BigDecimal("1560.00"));
        items.add(item2);

        dto.setLineItems(items);
        dto.setCurrency("EUR");
        dto.setSubtotal(new BigDecimal("1560.00"));
        dto.setDiscount(BigDecimal.ZERO);
        dto.setTotalAmount(new BigDecimal("1560.00"));

        FlowForceQuotePdfService pdfService = new FlowForceQuotePdfService();
        java.lang.reflect.Field logoField = FlowForceQuotePdfService.class.getDeclaredField("logoImagePath");
        logoField.setAccessible(true);
        logoField.set(pdfService, "static/images/logo.png");

        java.lang.reflect.Field sigField = FlowForceQuotePdfService.class.getDeclaredField("signatureImagePath");
        sigField.setAccessible(true);
        sigField.set(pdfService, "static/images/Umapathi-signature.png");

        byte[] pdfBytes = pdfService.generateFlowForceQuotePdf(dto);
        File outputFile = new File("c:/Users/NEW/Invoicehub/uploads/FF_BOS_MG2_800B_Simulated.pdf");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile)) {
            fos.write(pdfBytes);
        }
        System.out.println("Simulated BOS PDF generated successfully at: " + outputFile.getAbsolutePath());
    }}
