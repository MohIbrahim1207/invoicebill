package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.AiQuotationEditDto;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class FlowForceQuotePdfService {

    static {
        try {
            com.lowagie.text.FontFactory.registerDirectories();
        } catch (Exception e) {
            // ignore
        }
    }

    @org.springframework.beans.factory.annotation.Value("${flowforce.branding.logo:static/images/logo.png}")
    private String logoImagePath;

    @org.springframework.beans.factory.annotation.Value("${flowforce.branding.signature:static/images/Umapathi-signature.png}")
    private String signatureImagePath;
    
    // UI Theme colors from InvoiceHub matching design system
    private static final Color FLOWFORCE_ORANGE = new Color(220, 95, 45); // Warm/Orange accents
    private static final Color INVOICEHUB_BLUE = new Color(20, 74, 122); // Deep blue
    private static final Color INDUSTRIAL_GREEN = new Color(33, 114, 85); // Teal / Green
    private static final Color BORDER_GRAY = new Color(205, 211, 218);
    private static final Color HEADER_GRAY = new Color(238, 242, 246);
    private static final Color TEXT_DARK = new Color(32, 41, 55);
    private static final Color TEXT_MUTED = new Color(91, 102, 118);

    private static final String COMPANY_NAME = "Flow Force";
    private static final String COMPANY_ADDRESS = "123 Engineering Zone, Industrial Business District, Sector 5";
    private static final String COMPANY_EMAIL = "sales@flowforce.com";
    private static final String COMPANY_PHONE = "+1-800-FLOW-FORCE";

    public byte[] generateFlowForceQuotePdf(AiQuotationEditDto quoteDto) {
        if (quoteDto == null) {
            throw new IllegalArgumentException("Quotation data is required");
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // Load logo image
            byte[] logoBytes;
            try {
                String path = logoImagePath != null ? logoImagePath : "static/images/logo.png";
                org.springframework.core.io.Resource resource = new org.springframework.core.io.DefaultResourceLoader().getResource(
                    path.startsWith("classpath:") || path.startsWith("file:") ? path : "classpath:" + path
                );
                try (java.io.InputStream is = resource.getInputStream()) {
                    logoBytes = is.readAllBytes();
                }
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load logo image from configuration path: " + logoImagePath, e);
            }

            com.lowagie.text.Image logoImage = com.lowagie.text.Image.getInstance(logoBytes);
            float logoScaledWidth = 180f;
            float logoScaledHeight = (logoImage.getHeight() / logoImage.getWidth()) * logoScaledWidth;
            logoImage.scaleAbsolute(logoScaledWidth, logoScaledHeight);

            // Construct Header Table: Logo on Left, Coded Company Address Block on Right
            PdfPTable headerTable = new PdfPTable(new float[] {1f, 1f});
            headerTable.setTotalWidth(PageSize.A4.getWidth() - 72); // 523
            headerTable.setLockedWidth(true);

            PdfPCell logoCell = new PdfPCell();
            logoCell.setBorder(Rectangle.NO_BORDER);
            logoCell.addElement(logoImage);
            logoCell.setVerticalAlignment(Element.ALIGN_TOP);
            headerTable.addCell(logoCell);

            PdfPCell addressCell = new PdfPCell();
            addressCell.setBorder(Rectangle.NO_BORDER);
            addressCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            addressCell.setVerticalAlignment(Element.ALIGN_TOP);

            Paragraph addressPara = new Paragraph();
            addressPara.setAlignment(Element.ALIGN_RIGHT);
            addressPara.setLeading(9.5f); // Tight line spacing

            java.awt.Color headerColor = new java.awt.Color(0, 0, 100); // #000064
            Font boldFont = com.lowagie.text.FontFactory.getFont("Arial", 7.5f, Font.BOLD, headerColor);
            Font regularFont = com.lowagie.text.FontFactory.getFont("Arial", 7.5f, Font.NORMAL, headerColor);

            addressPara.add(new Chunk("PT. Flow Force Engineering\n", boldFont));
            addressPara.add(new Chunk("The Kensington Office Tower Unit C2\n" +
                    "3rd Floor, Jl. Boulevard Raya No.1\n" +
                    "Kelapa Gading, Jakarta 14240\n" +
                    "Indonesia\n", regularFont));
            addressPara.add(new Chunk("Phone : 021 4064 2030\n" +
                    "Email : flowforce_sin@flow-force.com\n" +
                    "Web : www.flow-force.com", regularFont));

            addressCell.addElement(addressPara);
            headerTable.addCell(addressCell);

            // Document margin at the top is 140 to accommodate header on every page
            Document document = new Document(PageSize.A4, 36, 36, 140, 36);
            PdfWriter writer = PdfWriter.getInstance(document, outputStream);
            
            // Set Page Event to draw header table and page numbers on every page
            final PdfPTable finalHeaderTable = headerTable;
            writer.setPageEvent(new com.lowagie.text.pdf.PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter writer, Document doc) {
                    try {
                        finalHeaderTable.writeSelectedRows(0, -1, 36, PageSize.A4.getHeight() - 15, writer.getDirectContent());
                        
                        // Draw footer page number: "Page X - 4"
                        String pageText = "Page " + writer.getPageNumber() + " - 4";
                        com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContent();
                        cb.beginText();
                        cb.setFontAndSize(com.lowagie.text.pdf.BaseFont.createFont(com.lowagie.text.pdf.BaseFont.HELVETICA, com.lowagie.text.pdf.BaseFont.CP1252, false), 7);
                        cb.setColorFill(TEXT_MUTED);
                        float x = PageSize.A4.getWidth() / 2;
                        float y = 20;
                        cb.showTextAligned(Element.ALIGN_CENTER, pageText, x, y, 0);
                        cb.endText();
                    } catch (Exception e) {
                        throw new RuntimeException("Error rendering header/footer on page", e);
                    }
                }
            });

            document.open();

            // Page 1
            generatePage1(document, quoteDto);
            
            // Page 2
            document.newPage();
            generatePage2(document, quoteDto);
            
            // Page 3
            document.newPage();
            generatePage3(document, quoteDto);
            
            // Page 4
            document.newPage();
            generatePage4(document, quoteDto);

            document.close();
            return outputStream.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("Failed to generate Flow Force quotation PDF", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate Flow Force quotation PDF", ex);
        }
    }

    private void generatePage1(Document document, AiQuotationEditDto quoteDto) throws DocumentException {
        // Customer Information table (To, Attention, Date, Ref., Subject)
        PdfPTable customerTable = new PdfPTable(new float[] {1.5f, 4.5f});
        customerTable.setWidthPercentage(45);
        customerTable.setHorizontalAlignment(Element.ALIGN_LEFT);
        customerTable.setSpacingAfter(18);
        
        addCustomerRow(customerTable, "To", safe(quoteDto.getCustomerName(), "N/A"));
        addCustomerRow(customerTable, "Attention", safe(quoteDto.getAttention(), "N/A"));
        addCustomerRow(customerTable, "Date", safe(quoteDto.getDate(), "N/A"));
        addCustomerRow(customerTable, "Ref.", safe(quoteDto.getReference(), "N/A"));
        addCustomerRow(customerTable, "Subject", safe(quoteDto.getSubject(), "N/A"));
        document.add(customerTable);

        // Product Introduction
        Paragraph introPara1 = new Paragraph("We are pleased to quote;", font(9, Font.NORMAL, TEXT_DARK));
        introPara1.setSpacingAfter(8);
        document.add(introPara1);

        String machineName = safe(quoteDto.getMachineName(), "BOS high pressure homogeniser");
        String machineModel = safe(quoteDto.getMachineModel(), "MG2-800B");
        String capacity = safe(quoteDto.getCapacity(), "80 l/h at 800 bar");
        
        String introText = "One " + machineName + " type " + machineModel + ".\n" +
                "The maximum capacity of this 3-piston homogeniser is " + capacity + ".\n" +
                "The machine has Ball type pump valves and replaceable pump valve seats and is therefore " +
                "suitable for viscous and abrasive products.\n" +
                "The " + machineModel + " model is designed for small scale operations of approx. 8-10 hours/day, 5-6 days/week.";
        
        Paragraph introPara2 = new Paragraph(introText, font(9, Font.NORMAL, TEXT_DARK));
        introPara2.setSpacingAfter(18);
        document.add(introPara2);

        // Product Information Table
        PdfPTable infoTable = new PdfPTable(new float[] {1.5f, 2.3f});
        infoTable.setWidthPercentage(100);
        
        addInfoRow(infoTable, "Machine type:", machineName + " " + machineModel);
        addInfoRow(infoTable, "Product:", safe(quoteDto.getProduct(), "Various products\nLab Homogeniser / Research"));
        addInfoRow(infoTable, "Capacity:", capacity);
        addInfoRow(infoTable, "Operating temperature:", safe(quoteDto.getOperatingTemperature(), "85°C max"));
        addInfoRow(infoTable, "Operating pressure:", safe(quoteDto.getOperatingPressure(), "800 bar max. Total pressure"));
        addInfoRow(infoTable, "Required feed pressure:", safe(quoteDto.getRequiredFeedPressure(), "2 bar + 0,05 bar per % solid content"));
        addInfoRow(infoTable, "Plunger diameter:", safe(quoteDto.getPlungerDiameter(), "10 mm – 3 pieces"));
        addInfoRow(infoTable, "Power consumption:", safe(quoteDto.getPowerConsumption(), "1,1 – 2,0 kW"));
        addInfoRow(infoTable, "Eccentric shaft speed:", safe(quoteDto.getEccentricShaftSpeed(), "99 – 199 rpm"));

        boolean imageRendered = false;
        if (quoteDto.getProductImageBase64() != null && !quoteDto.getProductImageBase64().isEmpty()) {
            try {
                byte[] imgBytes = java.util.Base64.getDecoder().decode(quoteDto.getProductImageBase64());
                com.lowagie.text.Image machineImage = com.lowagie.text.Image.getInstance(imgBytes);
                // Scale to fit the 2.2 column (approx width 170)
                float targetWidth = 170f;
                float targetHeight = (machineImage.getHeight() / machineImage.getWidth()) * targetWidth;
                
                // If too tall, cap height and scale width
                if (targetHeight > 220f) {
                    targetHeight = 220f;
                    targetWidth = (machineImage.getWidth() / machineImage.getHeight()) * targetHeight;
                }
                machineImage.scaleAbsolute(targetWidth, targetHeight);
                machineImage.setAlignment(Element.ALIGN_CENTER);

                // Construct layout table
                PdfPTable productLayoutTable = new PdfPTable(new float[] {3.8f, 2.2f});
                productLayoutTable.setWidthPercentage(100);
                productLayoutTable.setSpacingAfter(12);

                PdfPCell leftCell = new PdfPCell();
                leftCell.setBorder(Rectangle.NO_BORDER);
                leftCell.setPaddingRight(10);
                leftCell.addElement(infoTable);
                productLayoutTable.addCell(leftCell);

                PdfPCell rightCell = new PdfPCell();
                rightCell.setBorder(Rectangle.BOX);
                rightCell.setBorderColor(BORDER_GRAY);
                rightCell.setBackgroundColor(HEADER_GRAY);
                rightCell.setPadding(8);
                rightCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                rightCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                
                Paragraph spacingBefore = new Paragraph("\n", font(6, Font.NORMAL, TEXT_MUTED));
                rightCell.addElement(spacingBefore);
                rightCell.addElement(machineImage);

                productLayoutTable.addCell(rightCell);
                document.add(productLayoutTable);
                imageRendered = true;
            } catch (Exception e) {
                // Log/ignore and let it fallback to full-width table
            }
        }

        if (!imageRendered) {
            infoTable.setSpacingAfter(12);
            document.add(infoTable);
        }
    }

    private void generatePage2(Document document, AiQuotationEditDto quoteDto) throws DocumentException {
        // Technical Specifications
        Paragraph title = new Paragraph("Technical specifications:", font(10, Font.BOLD, FLOWFORCE_ORANGE));
        title.setSpacingBefore(6);
        title.setSpacingAfter(12);
        document.add(title);
        
        String specs = safe(quoteDto.getTechnicalSpecifications(), 
            "Cylinder block:\n" +
            "  - Forged “one-piece” cylinder block made of high tensile stainless steel.\n" +
            "    The design meets the highest sanitary standards and is suitable for C.I.P. cleaning.\n" +
            "  - Ball type pump valves with replaceable pump valve seats, made of wear resistant Rexalloy®.\n" +
            "  - Pressure transducer for Total pressure, with digital indicator in front panel.\n" +
            "  - Ceramic plungers – 3 pieces\n\n" +
            "Homogenising Valve:\n" +
            "  - Two-stage homogenising valve assembly, Manual operation with hand-wheels\n" +
            "  - Homogenising valve 1st state made of extra wear resistant Tungsten-carbide.\n" +
            "  - Homogenising valve 2nd stage made of wear resistant alloy Rexalloy/Stellite.\n\n" +
            "Base and sub-base:\n" +
            "  - Splash lubrication of all bearings and cross heads making the machine suitable for\n" +
            "    capacity variations of 50-100% under full load with frequency converter.\n" +
            "  - Stainless sub-base with in height adjustable feet.\n" +
            "  - Stainless steel cladding.\n\n" +
            "Connections and requirements:\n" +
            "  - Main motor: 2,2 kW-750 rpm, suitable for 400V-50Hz, 3Ph, incl. pulley drive for 40 – 80 l/h\n" +
            "  - Integrated control cabinet, fully functional, start/stop. Danfoss® FC-51 frequency\n" +
            "    converter for capacity variation of 50-100% with control in front panel.\n" +
            "  - For high viscose products, and for continuous operation, the machine requires a\n" +
            "    positive feed pressure of minimum >2 bar (not included in scope of supply)\n" +
            "  - For small scale testing a Feeding Hopper can be used (optional). First start the\n" +
            "    machine a full hopper with water. Once the hopper is nearly empty, start with the\n" +
            "    product. Keep the feeding hopper as full as possible to create product feeding pressure.\n" +
            "  - Cleaning with standard CIP solutions + hot water, afterwards flush thoroughly with clean\n" +
            "    water, Homogenizer pumping at normal speed, without homogenising pressure.\n" +
            "  - Solenoid valve for cooling water, incl. strainer\n" +
            "  - Plunger cooling water – 1 l/min, with flow restrictor.\n" +
            "  - Product inlet Tri-clamp\n" +
            "  - Product outlet Tri-clamp"
        );
        
        Paragraph specsPara = new Paragraph(specs, font(8, Font.NORMAL, TEXT_DARK));
        specsPara.setSpacingAfter(18);
        document.add(specsPara);

        // Dimensions and weight
        Paragraph dimTitle = new Paragraph("Dimensions and weight:", font(9, Font.BOLD, TEXT_DARK));
        dimTitle.setSpacingAfter(4);
        document.add(dimTitle);
        
        Paragraph dimValue = new Paragraph(safe(quoteDto.getDimensionsWeight(), "Height 85 cm. Length 60 cm. Width 55 cm. Weight 175 kg"), font(8, Font.NORMAL, TEXT_DARK));
        dimValue.setSpacingAfter(18);
        document.add(dimValue);

        // Delivery specifications
        Paragraph delTitle = new Paragraph("Delivery:", font(9, Font.BOLD, TEXT_DARK));
        delTitle.setSpacingAfter(4);
        document.add(delTitle);
        
        String delDetails = safe(quoteDto.getDeliveryDetails(), 
            "  - All electrical components, are prewired to the control cabinet with Frequency controller.\n" +
            "  - Performance testing with water, and quality inspection before shipment.\n" +
            "  - Machine will be supplied including lubrication oil, set of spare gaskets, instruction manual.\n" +
            "  - Machine will be cleaned, shrink wrapped and packaging in Wooden export crating."
        );
        Paragraph delPara = new Paragraph(delDetails, font(8, Font.NORMAL, TEXT_DARK));
        document.add(delPara);
    }

    private void generatePage3(Document document, AiQuotationEditDto quoteDto) throws DocumentException {
        Paragraph title = new Paragraph("Price summary :", font(10, Font.BOLD, FLOWFORCE_ORANGE));
        title.setSpacingBefore(6);
        title.setSpacingAfter(12);
        document.add(title);

        PdfPTable table = new PdfPTable(new float[] {1f, 6f, 1.5f, 1.5f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);

        // Header: Simple, non-colored, matching master template table header style
        PdfPCell sectionHeader = new PdfPCell(new Phrase("Section", font(8, Font.BOLD, TEXT_DARK)));
        sectionHeader.setBackgroundColor(HEADER_GRAY);
        sectionHeader.setBorderColor(BORDER_GRAY);
        sectionHeader.setPadding(7);
        sectionHeader.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(sectionHeader);

        PdfPCell descHeader = new PdfPCell(new Phrase("Description", font(8, Font.BOLD, TEXT_DARK)));
        descHeader.setBackgroundColor(HEADER_GRAY);
        descHeader.setBorderColor(BORDER_GRAY);
        descHeader.setPadding(7);
        table.addCell(descHeader);

        PdfPCell typeHeader = new PdfPCell(new Phrase("Type", font(8, Font.BOLD, TEXT_DARK)));
        typeHeader.setBackgroundColor(HEADER_GRAY);
        typeHeader.setBorderColor(BORDER_GRAY);
        typeHeader.setPadding(7);
        typeHeader.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(typeHeader);

        PdfPCell priceHeader = new PdfPCell(new Phrase("Total Price", font(8, Font.BOLD, TEXT_DARK)));
        priceHeader.setBackgroundColor(HEADER_GRAY);
        priceHeader.setBorderColor(BORDER_GRAY);
        priceHeader.setPadding(7);
        priceHeader.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(priceHeader);

        int sectionNum = 1;
        List<AiQuotationEditDto.LineItem> items = quoteDto.getLineItems();
        if (items != null && !items.isEmpty()) {
            for (AiQuotationEditDto.LineItem item : items) {
                String sectionStr = "1." + sectionNum;
                String desc = safe(item.getDescription(), item.getItemName());
                boolean isIncluded = item.getTotalPrice() == null || item.getTotalPrice().compareTo(BigDecimal.ZERO) == 0;
                String typeStr = isIncluded ? "Included" : "Option";
                String priceStr = formatPriceOrIncluded(item.getTotalPrice(), quoteDto.getCurrency());
                addFourColumnRow(table, sectionStr, desc, typeStr, priceStr);
                sectionNum++;
            }
        } else {
            // Default rows from BOS quotation including priced and "Included" items
            List<DefaultItem> defaultItems = List.of(
                new DefaultItem("Cylinder block made of 1.4418 stainless steel", "Included", "Included"),
                new DefaultItem("Ball valves made of Rexalloy/Stellite, suitable for viscose and abrasive products", "Included", "Included"),
                new DefaultItem("Ceramic plungers – 3 pieces, wear and corrosion resistant", "Included", "Included"),
                new DefaultItem("Cylinder block made of Duplex grade stainless steel, extra corrosion resistance", "€ 1.560,-", "Option"),
                new DefaultItem("Double packed cylinder block, suitable for aseptic processing", "€ 950,-", "Option"),
                new DefaultItem("Two stage homogenising valve assembly, manual with hand wheels, Rexalloy", "Included", "Included"),
                new DefaultItem("Homogenising valve in 2nd stage made of wear resistant alloy Rexalloy/Stellite", "Included", "Included"),
                new DefaultItem("Tungsten-carbide homogenising valve in 1st stage", "Included", "Included"),
                new DefaultItem("Pressure transducer for Total pressure, with digital indicator in front panel", "Included", "Included"),
                new DefaultItem("Pressure transducer for 2nd Stage pressure, with digital indicator in front panel", "€ 1.940,-", "Option"),
                new DefaultItem("Pressure transducer for inlet/feed pressure with digital indicator in front panel", "€ 1.430,-", "Option"),
                new DefaultItem("Pneumatic actuated homogenising valve assembly, for Remote pressure control by 4 – 20mA (customer supplied signal), requires 6 – 8 bar, incl. proportional control valves, air solenoid, air pressure gauges 2-stages.", "€ 4.500,-", "Option"),
                new DefaultItem("Solenoid for cooling water activation when starting the machine.", "Included", "Included"),
                new DefaultItem("Cooling water flow-switch, for switching off machine in case of lacking water", "€ 360,-", "Option"),
                new DefaultItem("Pressure relieve valve, set at 120% of max pressure (960 bar)", "€ 2.190,-", "Option"),
                new DefaultItem("Feeding hopper, sanitary design, ca. 5 liter", "Included", "Included"),
                new DefaultItem("Feeding hopper, sanitary design, ca. 5 liter, + by-pass valve", "€ 1.200,-", "Option"),
                new DefaultItem("Homogeniser mounted on mobile stainless steel frame", "€ 1.780,-", "Option"),
                new DefaultItem("Inlet/outlet counterparts", "€ 100,-", "Option"),
                new DefaultItem("Wooden Export crating", "Included", "Included")
            );
            for (DefaultItem item : defaultItems) {
                String sectionStr = "1." + sectionNum;
                addFourColumnRow(table, sectionStr, item.description, item.type, item.price);
                sectionNum++;
            }
        }

        // Add optional items
        List<AiQuotationEditDto.OptionalItem> optionals = quoteDto.getOptionalItems();
        if (optionals != null && !optionals.isEmpty()) {
            for (AiQuotationEditDto.OptionalItem item : optionals) {
                String sectionStr = "1." + sectionNum;
                String desc = safe(item.getItemName());
                if (item.getDescription() != null && !item.getDescription().isBlank() && !item.getDescription().equalsIgnoreCase(item.getItemName())) {
                    desc = desc + " - " + item.getDescription().trim();
                }
                boolean isIncluded = item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) == 0;
                String typeStr = isIncluded ? "Included" : "Option";
                String priceStr = formatPriceOrIncluded(item.getPrice(), quoteDto.getCurrency());
                addFourColumnRow(table, sectionStr, desc, typeStr, priceStr);
                sectionNum++;
            }
        }

        document.add(table);

        // Subtotal, Discount, Grand Total Table nested on the right
        PdfPTable wrapper = new PdfPTable(new float[] {5.5f, 4.5f});
        wrapper.setWidthPercentage(100);
        wrapper.addCell(emptyCell());
        
        PdfPTable summary = new PdfPTable(new float[] {2f, 2.5f});
        summary.setWidthPercentage(100);
        
        String cur = quoteDto.getCurrency() != null ? quoteDto.getCurrency() : "EUR";
        BigDecimal subtotal = moneyValue(quoteDto.getSubtotal());
        BigDecimal discount = moneyValue(quoteDto.getDiscount());
        BigDecimal grandTotal = moneyValue(quoteDto.getTotalAmount());
        if (grandTotal.equals(BigDecimal.ZERO) && !subtotal.equals(BigDecimal.ZERO)) {
            grandTotal = subtotal.subtract(discount);
        }

        addSummaryRow(summary, "Price", formatMoneyWithCurrency(subtotal, cur), false);
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            addSummaryRow(summary, "Special Discount", formatMoneyWithCurrency(discount, cur), false);
        }
        addSummaryRow(summary, "Total Price", formatMoneyWithCurrency(grandTotal, cur), true);
        
        PdfPCell summaryCell = new PdfPCell(summary);
        summaryCell.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(summaryCell);
        document.add(wrapper);
        
        Paragraph ppnText = new Paragraph("PPn taxes excluded", font(8, Font.NORMAL, TEXT_MUTED));
        ppnText.setAlignment(Element.ALIGN_RIGHT);
        document.add(ppnText);
    }

    private void generatePage4(Document document, AiQuotationEditDto quoteDto) throws DocumentException {
        // Terms & Conditions Table
        Paragraph title = new Paragraph("General Terms & Conditions:", font(10, Font.BOLD, FLOWFORCE_ORANGE));
        title.setSpacingBefore(6);
        title.setSpacingAfter(12);
        document.add(title);

        PdfPTable termsTable = new PdfPTable(new float[] {2.5f, 7.5f});
        termsTable.setWidthPercentage(100);
        termsTable.setSpacingAfter(18);

        addTermRow(termsTable, "Prices:", cleanTermsAndConditions(quoteDto.getPricesDescription(), "Incl. Machine as specified on page 1 – 2\nExcl. Extra options, Transportation, Installation, Commissioning"));
        addTermRow(termsTable, "Discount", cleanTermsAndConditions(quoteDto.getDiscountDescription(), "15% to dealer, still needs to be deducted from option list.\nDealer discount won’t be applied on rental conditions."));
        addTermRow(termsTable, "Terms of Payment", cleanTermsAndConditions(quoteDto.getPaymentTerms(), "30% with the order\n70% before shipment"));
        addTermRow(termsTable, "Delivery time:", cleanTermsAndConditions(quoteDto.getDeliveryTime(), "10 - 12 weeks"));
        addTermRow(termsTable, "Delivery terms:", cleanTermsAndConditions(quoteDto.getDeliveryTerms(), "EXW Hilversum, the Netherlands"));
        addTermRow(termsTable, "Warranty period:", cleanTermsAndConditions(quoteDto.getWarranty(), "2 year, with maximum of 4.000 operation hours."));
        addTermRow(termsTable, "Terms & Conditions:", cleanTermsAndConditions(quoteDto.getTermsConditions(), "PT. Flow Force Engineering Supply Conditions apply to this offer"));
        addTermRow(termsTable, "Quotation validity:", cleanTermsAndConditions(quoteDto.getValidity(), "30 days"));

        document.add(termsTable);

        // Signature Section
        byte[] signBytes;
        try {
            String path = signatureImagePath != null ? signatureImagePath : "static/images/Umapathi-signature.png";
            org.springframework.core.io.Resource resource = new org.springframework.core.io.DefaultResourceLoader().getResource(
                path.startsWith("classpath:") || path.startsWith("file:") ? path : "classpath:" + path
            );
            try (java.io.InputStream is = resource.getInputStream()) {
                signBytes = is.readAllBytes();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load signature image from configuration path: " + signatureImagePath, e);
        }

        com.lowagie.text.Image signImage;
        try {
            signImage = com.lowagie.text.Image.getInstance(signBytes);
            float scaledWidth = 90f;
            float scaledHeight = (signImage.getHeight() / signImage.getWidth()) * scaledWidth;
            signImage.scaleAbsolute(scaledWidth, scaledHeight);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create signature image instance", e);
        }

        Paragraph bestRegards = new Paragraph("Best regards\n\nYours Sincerely\n\nPT. FLOW FORCE ENGINEERING", font(9, Font.BOLD, TEXT_DARK));
        bestRegards.setSpacingAfter(4);
        document.add(bestRegards);

        signImage.setAlignment(Element.ALIGN_LEFT);
        document.add(signImage);

        Paragraph gmName = new Paragraph("Umapathi\nGeneral Manager", font(9, Font.BOLD, TEXT_DARK));
        gmName.setSpacingBefore(4);
        document.add(gmName);
    }

    private void addCustomerRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font(8, Font.BOLD, TEXT_DARK)));
        labelCell.setBorderColor(BORDER_GRAY);
        labelCell.setPadding(4);
        table.addCell(labelCell);
        
        PdfPCell valueCell = new PdfPCell(new Phrase(value, font(8, Font.NORMAL, TEXT_DARK)));
        valueCell.setBorderColor(BORDER_GRAY);
        valueCell.setPadding(4);
        table.addCell(valueCell);
    }

    private void addTermRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font(8, Font.BOLD, TEXT_DARK)));
        labelCell.setBorderColor(BORDER_GRAY);
        labelCell.setPadding(6);
        table.addCell(labelCell);
        
        PdfPCell valueCell = new PdfPCell(new Phrase(value, font(8, Font.NORMAL, TEXT_DARK)));
        valueCell.setBorderColor(BORDER_GRAY);
        valueCell.setPadding(6);
        table.addCell(valueCell);
    }

    private void addBodyCell(PdfPTable table, String text, int alignment, int fontStyle) {
        PdfPCell cell = new PdfPCell(new Phrase(safe(text), font(8, fontStyle, TEXT_DARK)));
        cell.setBorderColor(BORDER_GRAY);
        cell.setPadding(7);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private String formatMoneyWithCurrency(BigDecimal value, String currency) {
        String symbol = "";
        if ("EUR".equalsIgnoreCase(currency)) {
            symbol = "€ ";
        } else if ("USD".equalsIgnoreCase(currency)) {
            symbol = "$ ";
        } else if ("INR".equalsIgnoreCase(currency)) {
            symbol = "₹ ";
        } else {
            symbol = currency + " ";
        }
        return symbol + formatMoney(value) + ",-";
    }

    private PdfPTable twoColumnInfoTable() {
        PdfPTable table = new PdfPTable(new float[] {1.5f, 2.3f});
        table.setWidthPercentage(100);
        return table;
    }

    private void addSectionTitle(Document document, String title) throws DocumentException {
        Paragraph paragraph = new Paragraph(title.toUpperCase(), font(10, Font.BOLD, FLOWFORCE_ORANGE));
        paragraph.setSpacingBefore(6);
        paragraph.setSpacingAfter(6);
        document.add(paragraph);
    }

    private void addInfoRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font(8, Font.BOLD, TEXT_DARK)));
        labelCell.setBorderColor(BORDER_GRAY);
        labelCell.setBackgroundColor(HEADER_GRAY);
        labelCell.setPadding(6);
        table.addCell(labelCell);
        
        PdfPCell valueCell = new PdfPCell(new Phrase(safe(value), font(8, Font.NORMAL, TEXT_DARK)));
        valueCell.setBorderColor(BORDER_GRAY);
        valueCell.setPadding(6);
        table.addCell(valueCell);
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font(8, Font.BOLD, Color.WHITE)));
        cell.setBackgroundColor(INVOICEHUB_BLUE);
        cell.setBorderColor(INVOICEHUB_BLUE);
        cell.setPadding(7);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(safe(text), font(8, Font.NORMAL, TEXT_DARK)));
        cell.setBorderColor(BORDER_GRAY);
        cell.setPadding(7);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private void addSummaryRow(PdfPTable table, String label, String value, boolean emphasized) {
        Font labelFont = font(8, emphasized ? Font.BOLD : Font.NORMAL, TEXT_DARK);
        Font valueFont = font(8, Font.BOLD, emphasized ? INVOICEHUB_BLUE : TEXT_DARK);
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorderColor(BORDER_GRAY);
        labelCell.setPadding(6);
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorderColor(BORDER_GRAY);
        valueCell.setPadding(6);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (emphasized) {
            labelCell.setBackgroundColor(HEADER_GRAY);
            valueCell.setBackgroundColor(HEADER_GRAY);
        }
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addTerm(PdfPTable table, String label, String text) {
        Paragraph paragraph = new Paragraph();
        paragraph.add(new Chunk(label + ": ", font(8, Font.BOLD, TEXT_DARK)));
        paragraph.add(new Chunk(text, font(8, Font.NORMAL, TEXT_MUTED)));
        PdfPCell cell = cell("", 1, Element.ALIGN_LEFT);
        cell.addElement(paragraph);
        cell.setPadding(7);
        table.addCell(cell);
    }

    private void addSignatureCell(PdfPTable table, String label) {
        PdfPCell cell = cell("", 1, Element.ALIGN_CENTER);
        cell.setFixedHeight(74);
        cell.setVerticalAlignment(Element.ALIGN_BOTTOM);
        Paragraph line = new Paragraph("____________________________", font(9, Font.NORMAL, TEXT_MUTED));
        line.setAlignment(Element.ALIGN_CENTER);
        Paragraph caption = new Paragraph(label, font(8, Font.BOLD, TEXT_DARK));
        caption.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(line);
        cell.addElement(caption);
        table.addCell(cell);
    }

    private PdfPCell cell(String text, int borderWidth, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(safe(text), font(8, Font.NORMAL, TEXT_DARK)));
        cell.setBorderWidth(borderWidth);
        cell.setBorderColor(BORDER_GRAY);
        cell.setPadding(4);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    private PdfPCell emptyCell() {
        PdfPCell cell = new PdfPCell(new Phrase(""));
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    private Font font(float size, int style, Color color) {
        return new Font(Font.HELVETICA, size, style, color);
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal scaled = moneyValue(value);
        java.text.DecimalFormat formatter = (java.text.DecimalFormat) java.text.NumberFormat.getInstance(java.util.Locale.GERMANY);
        if (scaled.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
            formatter.applyPattern("#,##0");
        } else {
            formatter.applyPattern("#,##0.00");
        }
        return formatter.format(scaled);
    }

    private String formatQuantity(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        java.text.DecimalFormat formatter = (java.text.DecimalFormat) java.text.NumberFormat.getInstance(java.util.Locale.GERMANY);
        formatter.applyPattern("#,##0.##");
        return formatter.format(value);
    }

    private BigDecimal moneyValue(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String safe(String value) {
        return safe(value, "N/A");
    }

    private String safe(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String safe(String first, String second, String fallback) {
        return safe(first, safe(second, fallback));
    }

    private String cleanTermsAndConditions(String text, String fallback) {
        String val = safe(text, fallback);
        if (val == null) {
            return fallback;
        }
        // Remove common signatory lines to avoid duplicate signature blocks
        String cleaned = val
            .replaceAll("(?i)Best regards.*", "")
            .replaceAll("(?i)Yours Sincerely.*", "")
            .replaceAll("(?i)PT\\. FLOW FORCE ENGINEERING.*", "")
            .replaceAll("(?i)Umapathi.*", "")
            .replaceAll("(?i)General Manager.*", "")
            .trim();
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private void addSimpleTableRow(PdfPTable table, String desc, String price) {
        PdfPCell descCell = new PdfPCell(new Phrase(desc, font(8, Font.NORMAL, TEXT_DARK)));
        descCell.setBorderColor(BORDER_GRAY);
        descCell.setPadding(6);
        table.addCell(descCell);
        
        PdfPCell priceCell = new PdfPCell(new Phrase(price, font(8, Font.NORMAL, TEXT_DARK)));
        priceCell.setBorderColor(BORDER_GRAY);
        priceCell.setPadding(6);
        priceCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(priceCell);
    }

    private String formatPriceOrIncluded(BigDecimal price, String currency) {
        if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
            return "Included";
        }
        return formatMoneyWithCurrency(price, currency);
    }

    private void addFourColumnRow(PdfPTable table, String section, String desc, String type, String price) {
        PdfPCell sectionCell = new PdfPCell(new Phrase(section, font(8, Font.NORMAL, TEXT_DARK)));
        sectionCell.setBorderColor(BORDER_GRAY);
        sectionCell.setPadding(6);
        sectionCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(sectionCell);

        PdfPCell descCell = new PdfPCell(new Phrase(desc, font(8, Font.NORMAL, TEXT_DARK)));
        descCell.setBorderColor(BORDER_GRAY);
        descCell.setPadding(6);
        table.addCell(descCell);

        PdfPCell typeCell = new PdfPCell(new Phrase(type, font(8, Font.NORMAL, TEXT_DARK)));
        typeCell.setBorderColor(BORDER_GRAY);
        typeCell.setPadding(6);
        typeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(typeCell);
        
        PdfPCell priceCell = new PdfPCell(new Phrase(price, font(8, Font.NORMAL, TEXT_DARK)));
        priceCell.setBorderColor(BORDER_GRAY);
        priceCell.setPadding(6);
        priceCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(priceCell);
    }

    private static class DefaultItem {
        String description;
        String price;
        String type;
        
        DefaultItem(String description, String price, String type) {
            this.description = description;
            this.price = price;
            this.type = type;
        }
    }
}
