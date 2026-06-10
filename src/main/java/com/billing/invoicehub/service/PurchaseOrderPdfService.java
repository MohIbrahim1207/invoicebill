package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.PurchaseOrder;
import com.billing.invoicehub.entity.PurchaseOrderItem;
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
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PurchaseOrderPdfService {
    private static final Color INVOICEHUB_BLUE = new Color(20, 74, 122);
    private static final Color INDUSTRIAL_GREEN = new Color(33, 114, 85);
    private static final Color BORDER_GRAY = new Color(205, 211, 218);
    private static final Color HEADER_GRAY = new Color(238, 242, 246);
    private static final Color TEXT_DARK = new Color(32, 41, 55);
    private static final Color TEXT_MUTED = new Color(91, 102, 118);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final String COMPANY_NAME = "InvoiceHub";
    private static final String COMPANY_ADDRESS = "Procurement Office, Industrial Business District";
    private static final String COMPANY_EMAIL = "procurement@invoicehub.local";
    private static final String COMPANY_PHONE = "+1-800-123-4567";

    public byte[] generatePurchaseOrderPdf(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            throw new IllegalArgumentException("Purchase order is required");
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 32, 36);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            addHeader(document);
            addDocumentTitle(document);
            addPurchaseOrderInfo(document, purchaseOrder);
            addVendorInfo(document, purchaseOrder.getVendor());
            addItemTable(document, purchaseOrder);
            addSummary(document, purchaseOrder);
            addPaymentSection(document, purchaseOrder);
            addTerms(document);
            addSignatureSection(document);

            document.close();
            return outputStream.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("Failed to generate purchase order PDF", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate purchase order PDF", ex);
        }
    }

    private void addHeader(Document document) throws DocumentException {
        PdfPTable header = new PdfPTable(new float[] {1.2f, 4.2f, 2.8f});
        header.setWidthPercentage(100);
        header.setSpacingAfter(18);

        PdfPCell logoCell = cell("", 0, Element.ALIGN_CENTER);
        logoCell.setFixedHeight(58);
        logoCell.setBackgroundColor(INVOICEHUB_BLUE);
        Paragraph logo = new Paragraph("IH", font(24, Font.BOLD, Color.WHITE));
        logo.setAlignment(Element.ALIGN_CENTER);
        logoCell.addElement(logo);
        header.addCell(logoCell);

        PdfPCell companyCell = cell("", 0, Element.ALIGN_LEFT);
        companyCell.addElement(new Paragraph(COMPANY_NAME, font(18, Font.BOLD, TEXT_DARK)));
        companyCell.addElement(new Paragraph(COMPANY_ADDRESS, font(9, Font.NORMAL, TEXT_MUTED)));
        companyCell.addElement(new Paragraph("Email: " + COMPANY_EMAIL, font(9, Font.NORMAL, TEXT_MUTED)));
        companyCell.addElement(new Paragraph("Phone: " + COMPANY_PHONE, font(9, Font.NORMAL, TEXT_MUTED)));
        header.addCell(companyCell);

        PdfPCell titleCell = cell("", 0, Element.ALIGN_RIGHT);
        Paragraph title = new Paragraph("PURCHASE ORDER", font(16, Font.BOLD, INVOICEHUB_BLUE));
        title.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(title);
        Paragraph subtitle = new Paragraph("Industrial Procurement Document", font(8, Font.NORMAL, TEXT_MUTED));
        subtitle.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(subtitle);
        header.addCell(titleCell);

        document.add(header);
    }

    private void addDocumentTitle(Document document) throws DocumentException {
        PdfPTable band = new PdfPTable(1);
        band.setWidthPercentage(100);
        band.setSpacingAfter(12);
        PdfPCell cell = cell("Purchase Order", 9, Element.ALIGN_CENTER);
        cell.setBackgroundColor(HEADER_GRAY);
        cell.setPadding(8);
        cell.setBorderColor(BORDER_GRAY);
        cell.setPhrase(new Phrase("PURCHASE ORDER", font(13, Font.BOLD, INVOICEHUB_BLUE)));
        band.addCell(cell);
        document.add(band);
    }

    private void addPurchaseOrderInfo(Document document, PurchaseOrder purchaseOrder) throws DocumentException {
        addSectionTitle(document, "Purchase Order Information");
        PdfPTable table = twoColumnInfoTable();
        addInfoRow(table, "PO Number", safe(purchaseOrder.getPoNumber()), "PO Date", formatDate(purchaseOrder.getCreatedAt()));
        addInfoRow(table, "Delivery Date", formatDate(purchaseOrder.getDueDate()), "PO Status", resolvePoStatus(purchaseOrder));
        addInfoRow(table, "Payment Status", displayEnum(purchaseOrder.getPaymentStatus()), "Currency", safe(purchaseOrder.getCurrency(), "Default"));
        document.add(table);
    }

    private void addVendorInfo(Document document, AppUser vendor) throws DocumentException {
        addSectionTitle(document, "Vendor Information");
        PdfPTable table = twoColumnInfoTable();
        addInfoRow(table, "Vendor Name", vendorName(vendor), "Contact Person", vendor != null ? safe(vendor.getFullName(), vendor.getUsername()) : "N/A");
        addInfoRow(table, "Email", vendor != null ? safe(vendor.getEmail()) : "N/A", "Phone", vendor != null ? safe(vendor.getPhone()) : "N/A");
        addFullWidthInfoRow(table, "Address", vendor != null ? safe(vendor.getAddress()) : "N/A");
        document.add(table);
    }

    private void addItemTable(Document document, PurchaseOrder purchaseOrder) throws DocumentException {
        addSectionTitle(document, "Item Table");
        PdfPTable table = new PdfPTable(new float[] {0.7f, 2.2f, 3.2f, 1.0f, 1.4f, 1.4f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);

        addHeaderCell(table, "Sl No");
        addHeaderCell(table, "Item Name");
        addHeaderCell(table, "Description");
        addHeaderCell(table, "Quantity");
        addHeaderCell(table, "Unit Price");
        addHeaderCell(table, "Total");

        List<PurchaseOrderItem> items = purchaseOrder.getItems();
        if (items == null || items.isEmpty()) {
            BigDecimal total = moneyValue(purchaseOrder.getTotalAmount());
            addBodyCell(table, "1", Element.ALIGN_CENTER);
            addBodyCell(table, safe(purchaseOrder.getPoNumber(), "Purchase Order"), Element.ALIGN_LEFT);
            addBodyCell(table, safe(purchaseOrder.getDescription(), purchaseOrder.getNotes(), "Procurement item as per purchase order"), Element.ALIGN_LEFT);
            addBodyCell(table, "1.00", Element.ALIGN_CENTER);
            addBodyCell(table, formatMoney(total), Element.ALIGN_RIGHT);
            addBodyCell(table, formatMoney(total), Element.ALIGN_RIGHT);
        } else {
            int slNo = 1;
            for (PurchaseOrderItem item : items) {
                addBodyCell(table, String.valueOf(slNo++), Element.ALIGN_CENTER);
                addBodyCell(table, safe(item.getItemName(), "Purchase Order Item"), Element.ALIGN_LEFT);
                addBodyCell(table, safe(purchaseOrder.getDescription(), purchaseOrder.getNotes(), "As per purchase order"), Element.ALIGN_LEFT);
                addBodyCell(table, formatQuantity(item.getQuantity()), Element.ALIGN_CENTER);
                addBodyCell(table, formatMoney(item.getUnitPrice()), Element.ALIGN_RIGHT);
                addBodyCell(table, formatMoney(item.getLineTotal()), Element.ALIGN_RIGHT);
            }
        }

        document.add(table);
    }

    private void addSummary(Document document, PurchaseOrder purchaseOrder) throws DocumentException {
        addSectionTitle(document, "Summary");
        BigDecimal subtotal = moneyValue(purchaseOrder.getTotalAmount());
        BigDecimal taxAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal grandTotal = subtotal.add(taxAmount);

        PdfPTable wrapper = new PdfPTable(new float[] {4.7f, 2.3f});
        wrapper.setWidthPercentage(100);
        wrapper.setSpacingAfter(12);
        wrapper.addCell(emptyCell());

        PdfPTable summary = new PdfPTable(new float[] {1.3f, 1.0f});
        summary.setWidthPercentage(100);
        addSummaryRow(summary, "Subtotal", formatMoney(subtotal), false);
        addSummaryRow(summary, "Tax Amount", formatMoney(taxAmount), false);
        addSummaryRow(summary, "Grand Total", formatMoney(grandTotal), true);
        addSummaryRow(summary, "Paid Amount", formatMoney(purchaseOrder.getPaidAmount()), false);
        addSummaryRow(summary, "Balance Amount", formatMoney(purchaseOrder.getBalanceAmount()), true);

        PdfPCell summaryCell = cell("", 0, Element.ALIGN_RIGHT);
        summaryCell.addElement(summary);
        wrapper.addCell(summaryCell);
        document.add(wrapper);
    }

    private void addPaymentSection(Document document, PurchaseOrder purchaseOrder) throws DocumentException {
        addSectionTitle(document, "Payment Section");
        PdfPTable table = new PdfPTable(new float[] {1, 1, 1});
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);

        addStatusCell(table, "UNPAID", purchaseOrder.getPaymentStatus() != null && purchaseOrder.getPaymentStatus().name().equals("UNPAID"));
        addStatusCell(table, "PARTIALLY PAID", purchaseOrder.getPaymentStatus() != null && purchaseOrder.getPaymentStatus().name().equals("PARTIALLY_PAID"));
        addStatusCell(table, "PAID", purchaseOrder.getPaymentStatus() != null && purchaseOrder.getPaymentStatus().name().equals("PAID"));
        document.add(table);
    }

    private void addTerms(Document document) throws DocumentException {
        addSectionTitle(document, "Terms & Conditions");
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingAfter(18);
        addTerm(table, "Delivery terms", "Delivery must match the agreed purchase order schedule and accepted receiving standards.");
        addTerm(table, "Payment terms", "Payment will be processed against valid delivery confirmation and approved invoice documentation.");
        addTerm(table, "Warranty terms", "Supplier warrants that supplied goods or services comply with agreed specifications and applicable quality standards.");
        document.add(table);
    }

    private void addSignatureSection(Document document) throws DocumentException {
        addSectionTitle(document, "Signature Section");
        PdfPTable table = new PdfPTable(new float[] {1, 1});
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);

        addSignatureCell(table, "Authorized By");
        addSignatureCell(table, "Vendor Signature");
        document.add(table);
    }

    private PdfPTable twoColumnInfoTable() {
        PdfPTable table = new PdfPTable(new float[] {1.2f, 2.3f, 1.2f, 2.3f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);
        return table;
    }

    private void addSectionTitle(Document document, String title) throws DocumentException {
        Paragraph paragraph = new Paragraph(title.toUpperCase(), font(10, Font.BOLD, INDUSTRIAL_GREEN));
        paragraph.setSpacingBefore(6);
        paragraph.setSpacingAfter(6);
        document.add(paragraph);
    }

    private void addInfoRow(PdfPTable table, String leftLabel, String leftValue, String rightLabel, String rightValue) {
        addLabelCell(table, leftLabel);
        addValueCell(table, leftValue);
        addLabelCell(table, rightLabel);
        addValueCell(table, rightValue);
    }

    private void addFullWidthInfoRow(PdfPTable table, String label, String value) {
        addLabelCell(table, label);
        PdfPCell valueCell = valueCell(value);
        valueCell.setColspan(3);
        table.addCell(valueCell);
    }

    private void addLabelCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font(8, Font.BOLD, TEXT_MUTED)));
        cell.setBackgroundColor(HEADER_GRAY);
        cell.setBorderColor(BORDER_GRAY);
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void addValueCell(PdfPTable table, String text) {
        table.addCell(valueCell(text));
    }

    private PdfPCell valueCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(safe(text), font(8, Font.NORMAL, TEXT_DARK)));
        cell.setBorderColor(BORDER_GRAY);
        cell.setPadding(6);
        return cell;
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

    private void addStatusCell(PdfPTable table, String status, boolean active) {
        PdfPCell cell = new PdfPCell(new Phrase((active ? "[X] " : "") + status, font(9, Font.BOLD, active ? Color.WHITE : TEXT_MUTED)));
        cell.setBackgroundColor(active ? INDUSTRIAL_GREEN : HEADER_GRAY);
        cell.setBorderColor(active ? INDUSTRIAL_GREEN : BORDER_GRAY);
        cell.setPadding(9);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
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

    private String resolvePoStatus(PurchaseOrder purchaseOrder) {
        if (purchaseOrder.getPoStatus() != null && !purchaseOrder.getPoStatus().isBlank()) {
            return purchaseOrder.getPoStatus();
        }
        return purchaseOrder.isActive() ? "Active" : "Inactive";
    }

    private String vendorName(AppUser vendor) {
        if (vendor == null) {
            return "N/A";
        }
        return safe(vendor.getCompanyName(), vendor.getUsername(), vendor.getFullName(), "N/A");
    }

    private String displayEnum(Enum<?> value) {
        if (value == null) {
            return "N/A";
        }
        return value.name().replace('_', ' ');
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? "N/A" : value.toLocalDate().format(DATE_FORMATTER);
    }

    private String formatDate(LocalDate value) {
        return value == null ? "N/A" : value.format(DATE_FORMATTER);
    }

    private String formatMoney(BigDecimal value) {
        return moneyValue(value).toPlainString();
    }

    private String formatQuantity(BigDecimal value) {
        return moneyValue(value).stripTrailingZeros().toPlainString();
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

    private String safe(String first, String second, String third, String fallback) {
        return safe(first, safe(second, safe(third, fallback)));
    }
}
