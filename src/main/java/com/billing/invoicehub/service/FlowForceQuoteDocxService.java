package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.AiQuotationEditDto;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.math.BigInteger;
import java.util.List;

@Service
public class FlowForceQuoteDocxService {

    private static final Logger logger = LoggerFactory.getLogger(FlowForceQuoteDocxService.class);

    @Value("${flowforce.branding.logo:static/images/logo.png}")
    private String logoImagePath;

    public byte[] generateFlowForceQuoteDocx(AiQuotationEditDto quoteDto) {
        if (quoteDto == null) {
            throw new IllegalArgumentException("Quotation data is required");
        }

        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Header Table: Left = Logo, Right = Company Address
            XWPFTable headerTable = document.createTable(1, 2);
            headerTable.setCellMargins(0, 0, 0, 0);
            headerTable.removeBorders();
            headerTable.setWidth("100%");

            XWPFTableCell logoCell = headerTable.getRow(0).getCell(0);
            logoCell.setWidth("50%");
            XWPFParagraph logoPara = logoCell.getParagraphs().get(0);

            byte[] logoBytes = loadLogoBytes();
            if (logoBytes != null && logoBytes.length > 0) {
                try (InputStream is = new ByteArrayInputStream(logoBytes)) {
                    logoPara.createRun().addPicture(is, XWPFDocument.PICTURE_TYPE_PNG, "logo.png", Units.toEMU(150), Units.toEMU(45));
                } catch (Exception e) {
                    logger.warn("Could not insert logo image into DOCX: {}", e.getMessage());
                }
            }

            XWPFTableCell addressCell = headerTable.getRow(0).getCell(1);
            addressCell.setWidth("50%");
            XWPFParagraph addressPara = addressCell.getParagraphs().get(0);
            addressPara.setAlignment(ParagraphAlignment.RIGHT);

            XWPFRun r1 = addressPara.createRun();
            r1.setBold(true);
            r1.setFontSize(8);
            r1.setColor("000064");
            r1.setText("PT. Flow Force Engineering\n");

            XWPFRun r2 = addressPara.createRun();
            r2.setFontSize(8);
            r2.setColor("000064");
            r2.setText("The Kensington Office Tower Unit C2\n3rd Floor, Jl. Boulevard Raya No.1\nKelapa Gading, Jakarta 14240\nIndonesia\nPhone : 021 4064 2030\nEmail : flowforce_sin@flow-force.com\nWeb : www.flow-force.com");

            addSpacing(document);

            // Customer Info Box
            XWPFTable customerTable = document.createTable();
            customerTable.setWidth("45%");
            addCustRow(customerTable, true, "To", safe(quoteDto.getCustomerName(), "Not Specified"));
            addCustRow(customerTable, false, "Attention", safe(quoteDto.getAttention(), "N/A"));
            addCustRow(customerTable, false, "Date", safe(quoteDto.getDate(), "N/A"));
            addCustRow(customerTable, false, "Ref.", safe(quoteDto.getReference(), "N/A"));
            addCustRow(customerTable, false, "Subject", safe(quoteDto.getSubject(), "N/A"));

            addSpacing(document);

            // Product Intro
            XWPFParagraph introPara = document.createParagraph();
            XWPFRun introRun = introPara.createRun();
            introRun.setText("We are pleased to quote;");
            introRun.setFontSize(9.5);

            String machineName = safe(quoteDto.getMachineName(), "BOS high pressure homogeniser");
            String machineModel = safe(quoteDto.getMachineModel(), "MG2-800B");
            String capacity = safe(quoteDto.getCapacity(), "80 l/h at 800 bar");

            XWPFParagraph descPara = document.createParagraph();
            XWPFRun descRun = descPara.createRun();
            descRun.setFontSize(9.5);
            descRun.setText("One " + machineName + " type " + machineModel + ".\n" +
                    "The maximum capacity of this 3-piston homogeniser is " + capacity + ".\n" +
                    "The machine has Ball type pump valves and replaceable pump valve seats and is therefore suitable for viscous and abrasive products.\n" +
                    "The " + machineModel + " model is designed for small scale operations of approx. 8-10 hours/day, 5-6 days/week.");

            addSpacing(document);

            // Technical Information Table
            XWPFTable infoTable = document.createTable();
            infoTable.setWidth("100%");
            addInfoRowDocx(infoTable, true, "Machine type:", machineName + " " + machineModel);
            addInfoRowDocx(infoTable, false, "Product:", safe(quoteDto.getProduct(), "Various products\nLab Homogeniser / Research"));
            addInfoRowDocx(infoTable, false, "Capacity:", capacity);
            addInfoRowDocx(infoTable, false, "Operating temperature:", safe(quoteDto.getOperatingTemperature(), "85°C max"));
            addInfoRowDocx(infoTable, false, "Operating pressure:", safe(quoteDto.getOperatingPressure(), "800 bar max. Total pressure"));
            addInfoRowDocx(infoTable, false, "Required feed pressure:", safe(quoteDto.getRequiredFeedPressure(), "2 bar + 0,05 bar per % solid content"));
            addInfoRowDocx(infoTable, false, "Plunger diameter:", safe(quoteDto.getPlungerDiameter(), "10 mm – 3 pieces"));
            addInfoRowDocx(infoTable, false, "Power consumption:", safe(quoteDto.getPowerConsumption(), "1,1 – 2,0 kW"));
            addInfoRowDocx(infoTable, false, "Eccentric shaft speed:", safe(quoteDto.getEccentricShaftSpeed(), "99 – 199 rpm"));

            addSpacing(document);

            // Price Summary Header
            XWPFParagraph priceHeadPara = document.createParagraph();
            XWPFRun priceHeadRun = priceHeadPara.createRun();
            priceHeadRun.setBold(true);
            priceHeadRun.setFontSize(11);
            priceHeadRun.setColor("C80000");
            priceHeadRun.setText("Price summary :");

            // Line Items Table
            List<AiQuotationEditDto.LineItem> items = quoteDto.getLineItems();
            XWPFTable itemsTable = document.createTable();
            itemsTable.setWidth("100%");

            // Header Row
            XWPFTableRow tableHeaderRow = itemsTable.getRow(0);
            setRowHeader(tableHeaderRow, "Section", "Description", "Type", "Total Price");

            if (items != null && !items.isEmpty()) {
                int sec = 1;
                for (AiQuotationEditDto.LineItem item : items) {
                    XWPFTableRow row = itemsTable.createRow();
                    setRowData(row, "1." + sec, safe(item.getItemName(), "") + " " + safe(item.getDescription(), ""), "Option", formatPrice(item.getTotalPrice(), quoteDto.getCurrency()));
                    sec++;
                }
            } else {
                XWPFTableRow row = itemsTable.createRow();
                setRowData(row, "1.1", safe(quoteDto.getSubject(), "Main Equipment Unit"), "Option", formatPrice(quoteDto.getSubtotal(), quoteDto.getCurrency()));
            }

            // Totals Row
            XWPFTableRow subtotalRow = itemsTable.createRow();
            setRowData(subtotalRow, "", "Price", "", formatPrice(quoteDto.getSubtotal(), quoteDto.getCurrency()));

            XWPFTableRow totalRow = itemsTable.createRow();
            setRowDataBold(totalRow, "", "Total Price", "", formatPrice(quoteDto.getTotalAmount(), quoteDto.getCurrency()));

            addSpacing(document);

            // General Terms & Conditions
            XWPFParagraph termsHeadPara = document.createParagraph();
            XWPFRun termsHeadRun = termsHeadPara.createRun();
            termsHeadRun.setBold(true);
            termsHeadRun.setFontSize(11);
            termsHeadRun.setColor("C80000");
            termsHeadRun.setText("General Terms & Conditions:");

            XWPFTable termsTable = document.createTable();
            termsTable.setWidth("100%");
            addTermRowDocx(termsTable, true, "Prices:", clean(quoteDto.getPricesDescription(), "Incl. Machine as specified on page 1 – 2\nExcl. Extra options, Transportation, Installation, Commissioning"));
            addTermRowDocx(termsTable, false, "Discount:", clean(quoteDto.getDiscountDescription(), "15% to dealer, still needs to be deducted from option list.\nDealer discount won’t be applied on rental conditions."));
            addTermRowDocx(termsTable, false, "Terms of Payment:", clean(quoteDto.getPaymentTerms(), "30% with the order\n70% before shipment"));
            addTermRowDocx(termsTable, false, "Delivery time:", clean(quoteDto.getDeliveryTime(), "10 - 12 weeks"));
            addTermRowDocx(termsTable, false, "Delivery terms:", clean(quoteDto.getDeliveryTerms(), "EXW Hilversum, the Netherlands"));
            addTermRowDocx(termsTable, false, "Warranty period:", clean(quoteDto.getWarranty(), "2 year, with maximum of 4.000 operation hours."));
            addTermRowDocx(termsTable, false, "Terms & Conditions:", clean(quoteDto.getTermsConditions(), "PT. Flow Force Engineering Supply Conditions apply to this offer"));
            addTermRowDocx(termsTable, false, "Quotation validity:", clean(quoteDto.getValidity(), "30 days"));

            addSpacing(document);

            // Closing Sign-Off (NO digital signature image)
            XWPFParagraph closingPara = document.createParagraph();
            XWPFRun closingRun = closingPara.createRun();
            closingRun.setBold(true);
            closingRun.setFontSize(9.5);
            closingRun.setText("Best regards\n\nYours Sincerely\n\nPT. FLOW FORCE ENGINEERING\n\n\nUmapathi\nGeneral Manager");

            document.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            logger.error("[DOCX GENERATION FAILED] Error generating Word quotation: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to generate Flow Force Word quotation: " + e.getMessage(), e);
        }
    }

    private byte[] loadLogoBytes() {
        try {
            String path = logoImagePath != null ? logoImagePath : "static/images/logo.png";
            org.springframework.core.io.Resource resource = new org.springframework.core.io.DefaultResourceLoader().getResource(
                    path.startsWith("classpath:") || path.startsWith("file:") ? path : "classpath:" + path
            );
            try (InputStream is = resource.getInputStream()) {
                return is.readAllBytes();
            }
        } catch (Exception e) {
            logger.warn("Failed to load logo image for DOCX: {}", e.getMessage());
            return null;
        }
    }

    private void addCustRow(XWPFTable table, boolean isFirst, String label, String value) {
        XWPFTableRow row = isFirst ? table.getRow(0) : table.createRow();
        XWPFTableCell c1 = row.getCell(0);
        c1.setText(label);
        XWPFTableCell c2 = row.createCell();
        c2.setText(value);
    }

    private void addInfoRowDocx(XWPFTable table, boolean isFirst, String label, String value) {
        XWPFTableRow row = isFirst ? table.getRow(0) : table.createRow();
        XWPFTableCell c1 = row.getCell(0);
        c1.setWidth("35%");
        XWPFParagraph p1 = c1.getParagraphs().get(0);
        XWPFRun r1 = p1.createRun();
        r1.setBold(true);
        r1.setFontSize(9);
        r1.setText(label);

        XWPFTableCell c2 = row.createCell();
        c2.setWidth("65%");
        XWPFParagraph p2 = c2.getParagraphs().get(0);
        XWPFRun r2 = p2.createRun();
        r2.setFontSize(9);
        r2.setText(value);
    }

    private void addTermRowDocx(XWPFTable table, boolean isFirst, String label, String value) {
        XWPFTableRow row = isFirst ? table.getRow(0) : table.createRow();
        XWPFTableCell c1 = row.getCell(0);
        c1.setWidth("30%");
        XWPFParagraph p1 = c1.getParagraphs().get(0);
        XWPFRun r1 = p1.createRun();
        r1.setBold(true);
        r1.setFontSize(9);
        r1.setText(label);

        XWPFTableCell c2 = row.createCell();
        c2.setWidth("70%");
        XWPFParagraph p2 = c2.getParagraphs().get(0);
        XWPFRun r2 = p2.createRun();
        r2.setFontSize(9);
        r2.setText(value);
    }

    private void setRowHeader(XWPFTableRow row, String c1, String c2, String c3, String c4) {
        row.getCell(0).setText(c1);
        row.createCell().setText(c2);
        row.createCell().setText(c3);
        row.createCell().setText(c4);
    }

    private void setRowData(XWPFTableRow row, String c1, String c2, String c3, String c4) {
        row.getCell(0).setText(c1);
        row.createCell().setText(c2);
        row.createCell().setText(c3);
        row.createCell().setText(c4);
    }

    private void setRowDataBold(XWPFTableRow row, String c1, String c2, String c3, String c4) {
        setRowData(row, c1, c2, c3, c4);
    }

    private void addSpacing(XWPFDocument doc) {
        doc.createParagraph();
    }

    private String safe(String str, String fallback) {
        return (str != null && !str.isBlank()) ? str.trim() : fallback;
    }

    private String clean(String str, String fallback) {
        return safe(str, fallback);
    }

    private String formatPrice(BigDecimal val, String curr) {
        if (val == null) return (curr != null ? curr : "$") + " 0.00";
        return (curr != null ? curr : "$") + " " + val.setScale(2, RoundingMode.HALF_UP).toString();
    }
}
