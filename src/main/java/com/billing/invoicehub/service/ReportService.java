package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.WeeklyTicketReportDto;
import com.billing.invoicehub.entity.TicketStatus;
import com.billing.invoicehub.entity.VendorTicket;
import com.billing.invoicehub.repository.VendorTicketRepository;
import com.billing.invoicehub.util.FormatUtils;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.HeaderFooter;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ReportService {

    private final VendorTicketRepository vendorTicketRepository;

    public ReportService(VendorTicketRepository vendorTicketRepository) {
        this.vendorTicketRepository = vendorTicketRepository;
    }

    public List<VendorTicket> getFilteredTickets(LocalDateTime start, LocalDateTime end, TicketStatus status,
            Long vendorId, Long clientId, String poNumber, String search) {
        return vendorTicketRepository.searchTicketsAdvanced(start, end, status, vendorId, clientId, poNumber, search);
    }

    public WeeklyTicketReportDto calculateStatistics(List<VendorTicket> tickets) {
        WeeklyTicketReportDto dto = new WeeklyTicketReportDto();
        long total = tickets.size();
        long open = 0, inProgress = 0, resolved = 0, revise = 0, cancel = 0;

        for (VendorTicket t : tickets) {
            if (t.getStatusRequest() == null)
                continue;
            switch (t.getStatusRequest()) {
                case OPEN -> open++;
                case IN_PROGRESS -> inProgress++;
                case RESOLVED, PARTIALLY_PAID -> resolved++;
                case REVISE -> revise++;
                case CANCEL -> cancel++;
            }
        }

        dto.setTotalCreated(total);
        dto.setPending(open);
        dto.setInProgress(inProgress);
        dto.setPaid(resolved);
        dto.setRejected(revise);
        dto.setCancelled(cancel);

        // Daily Counts (Past 7 Days relative to now)
        List<String> days = new ArrayList<>();
        List<Long> dailyCounts = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);

        for (int i = 6; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            days.add(d.format(formatter));
            long count = 0;
            for (VendorTicket t : tickets) {
                if (t.getCreatedAt() != null && t.getCreatedAt().toLocalDate().equals(d)) {
                    count++;
                }
            }
            dailyCounts.add(count);
        }
        dto.setDays(days);
        dto.setDailyCounts(dailyCounts);

        return dto;
    }

    public byte[] generateCsv(List<VendorTicket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return "No records found\n".getBytes(StandardCharsets.UTF_8);
        }

        StringBuilder csv = new StringBuilder();
        // UTF-8 Byte Order Mark (BOM) to support correct encoding in Excel
        csv.append('\ufeff');
        csv.append("Ticket ID,Vendor,Client,PO Number,Invoice Number,Invoice Amount,Status,Created Date\n");

        DateTimeFormatter dateOnly = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (VendorTicket t : tickets) {
            csv.append(csvEscape(t.getTicketNo())).append(",")
                    .append(csvEscape(t.getVendor() != null ? t.getVendor().getUsername() : "")).append(",")
                    .append(csvEscape(t.getClient() != null ? t.getClient().getCompanyName() : "")).append(",")
                    .append(csvEscape(t.getPoNumber())).append(",")
                    .append(csvEscape(t.getInvoiceNo())).append(",")
                    .append(csvEscape(FormatUtils.formatCurrency(t.getAmount(), t.getCurrency()))).append(",")
                    .append(csvEscape(t.getStatusRequest() != null ? t.getStatusRequest().name() : "")).append(",")
                    .append(csvEscape(t.getCreatedAt() != null ? t.getCreatedAt().format(dateOnly) : ""))
                    .append("\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvEscape(String val) {
        if (val == null)
            return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    public byte[] generateExcel(List<VendorTicket> tickets) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Filtered Report");

            // Fonts & Styles
            java.awt.Color headerColor = new java.awt.Color(20, 74, 122);
            org.apache.poi.xssf.usermodel.XSSFColor xssfHeaderColor = new org.apache.poi.xssf.usermodel.XSSFColor(
                    headerColor, null);

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.xssf.usermodel.XSSFFont headerFont = (org.apache.poi.xssf.usermodel.XSSFFont) workbook
                    .createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(xssfHeaderColor);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle borderStyle = workbook.createCellStyle();
            borderStyle.setBorderBottom(BorderStyle.THIN);
            borderStyle.setBorderTop(BorderStyle.THIN);
            borderStyle.setBorderRight(BorderStyle.THIN);
            borderStyle.setBorderLeft(BorderStyle.THIN);

            // Header row
            Row headerRow = sheet.createRow(0);
            String[] columns = { "Ticket ID", "Vendor", "Client", "PO Number", "Invoice Number", "Invoice Amount",
                    "Status", "Created Date" };
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            if (tickets == null || tickets.isEmpty()) {
                Row row = sheet.createRow(1);
                Cell cell = row.createCell(0);
                cell.setCellValue("No records found");
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, columns.length - 1));
            } else {
                int rowIdx = 1;
                DateTimeFormatter dateOnly = DateTimeFormatter.ofPattern("yyyy-MM-dd");

                for (VendorTicket t : tickets) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(t.getTicketNo() != null ? t.getTicketNo() : "");
                    row.createCell(1).setCellValue(t.getVendor() != null ? t.getVendor().getUsername() : "");
                    row.createCell(2).setCellValue(t.getClient() != null ? t.getClient().getCompanyName() : "");
                    row.createCell(3).setCellValue(t.getPoNumber() != null ? t.getPoNumber() : "");
                    row.createCell(4).setCellValue(t.getInvoiceNo() != null ? t.getInvoiceNo() : "");
                    row.createCell(5).setCellValue(FormatUtils.formatCurrency(t.getAmount(), t.getCurrency()));
                    row.createCell(6).setCellValue(t.getStatusRequest() != null ? t.getStatusRequest().name() : "");
                    row.createCell(7).setCellValue(t.getCreatedAt() != null ? t.getCreatedAt().format(dateOnly) : "");

                    for (int c = 0; c < columns.length; c++) {
                        row.getCell(c).setCellStyle(borderStyle);
                    }
                }
            }

            // Auto-size columns
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generatePdf(List<VendorTicket> tickets, Map<String, String> appliedFilters,
            WeeklyTicketReportDto summary) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);

            // Colors
            Color themeColor = new Color(20, 74, 122);
            Color textDark = new Color(32, 41, 55);
            Color textMuted = new Color(91, 102, 118);
            Color borderGray = new Color(205, 211, 218);
            Color headerGray = new Color(238, 242, 246);

            Font boldTitle = new Font(Font.HELVETICA, 18, Font.BOLD, Color.WHITE);
            Font regularBold = new Font(Font.HELVETICA, 9, Font.BOLD, textDark);
            Font regularMuted = new Font(Font.HELVETICA, 8, Font.NORMAL, textMuted);
            Font bodyFont = new Font(Font.HELVETICA, 8, Font.NORMAL, textDark);

            // Footer with page numbers
            HeaderFooter footer = new HeaderFooter(new Phrase("Page ", regularMuted), true);
            footer.setBorder(HeaderFooter.NO_BORDER);
            footer.setAlignment(HeaderFooter.ALIGN_CENTER);
            document.setFooter(footer);

            PdfWriter.getInstance(document, out);
            document.open();

            // Header Banner
            PdfPTable header = new PdfPTable(new float[] { 1.5f, 5.5f });
            header.setWidthPercentage(100);
            header.setSpacingAfter(15);

            PdfPCell logoCell = new PdfPCell();
            logoCell.setBackgroundColor(themeColor);
            logoCell.setFixedHeight(50);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            logoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            Paragraph logoText = new Paragraph("IH", boldTitle);
            logoText.setAlignment(Element.ALIGN_CENTER);
            logoCell.addElement(logoText);
            header.addCell(logoCell);

            PdfPCell infoCell = new PdfPCell();
            infoCell.setPadding(8);
            infoCell.setBorder(PdfPCell.NO_BORDER);
            infoCell.addElement(
                    new Paragraph("InvoiceHub Reports Portal", new Font(Font.HELVETICA, 14, Font.BOLD, themeColor)));
            infoCell.addElement(new Paragraph(
                    "Generated on: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    regularMuted));
            header.addCell(infoCell);
            document.add(header);

            // Applied Filters
            Paragraph filtersTitle = new Paragraph("APPLIED FILTERS",
                    new Font(Font.HELVETICA, 10, Font.BOLD, themeColor));
            filtersTitle.setSpacingAfter(5);
            document.add(filtersTitle);

            PdfPTable filterTable = new PdfPTable(2);
            filterTable.setWidthPercentage(100);
            filterTable.setSpacingAfter(15);
            for (Map.Entry<String, String> entry : appliedFilters.entrySet()) {
                PdfPCell labelCell = new PdfPCell(new Phrase(entry.getKey(), regularBold));
                labelCell.setBackgroundColor(headerGray);
                labelCell.setBorderColor(borderGray);
                labelCell.setPadding(4);

                PdfPCell valueCell = new PdfPCell(new Phrase(
                        entry.getValue() != null && !entry.getValue().isBlank() ? entry.getValue() : "ALL", bodyFont));
                valueCell.setBorderColor(borderGray);
                valueCell.setPadding(4);

                filterTable.addCell(labelCell);
                filterTable.addCell(valueCell);
            }
            document.add(filterTable);

            // Summary Stats Card Layout
            Paragraph summaryTitle = new Paragraph("TICKET STATISTICS SUMMARY",
                    new Font(Font.HELVETICA, 10, Font.BOLD, themeColor));
            summaryTitle.setSpacingAfter(5);
            document.add(summaryTitle);

            PdfPTable summaryTable = new PdfPTable(6);
            summaryTable.setWidthPercentage(100);
            summaryTable.setSpacingAfter(15);

            String[] statLabels = { "Total Tickets", "Pending", "In Progress", "Paid", "Rejected", "Cancelled" };
            long[] statValues = { summary.getTotalCreated(), summary.getPending(), summary.getInProgress(),
                    summary.getPaid(), summary.getRejected(), summary.getCancelled() };

            for (int i = 0; i < 6; i++) {
                PdfPCell cell = new PdfPCell();
                cell.setBorderColor(borderGray);
                cell.setPadding(6);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);

                Paragraph lbl = new Paragraph(statLabels[i], regularMuted);
                lbl.setAlignment(Element.ALIGN_CENTER);
                Paragraph val = new Paragraph(String.valueOf(statValues[i]),
                        new Font(Font.HELVETICA, 12, Font.BOLD, themeColor));
                val.setAlignment(Element.ALIGN_CENTER);

                cell.addElement(lbl);
                cell.addElement(val);
                summaryTable.addCell(cell);
            }
            document.add(summaryTable);

            // Detailed Tickets Table
            Paragraph detailTitle = new Paragraph("DETAILED TICKET REPORT",
                    new Font(Font.HELVETICA, 10, Font.BOLD, themeColor));
            detailTitle.setSpacingAfter(5);
            document.add(detailTitle);

            PdfPTable mainTable = new PdfPTable(new float[] { 1.2f, 1.2f, 1.4f, 1.2f, 1.2f, 1.2f, 1.0f, 1.4f });
            mainTable.setWidthPercentage(100);
            mainTable.setSpacingAfter(15);

            String[] headers = { "Ticket ID", "Vendor", "Client", "PO Number", "Invoice Number", "Invoice Amount",
                    "Status", "Created Date" };
            for (String h : headers) {
                PdfPCell hCell = new PdfPCell(new Phrase(h, new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE)));
                hCell.setBackgroundColor(themeColor);
                hCell.setBorderColor(themeColor);
                hCell.setPadding(5);
                hCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                mainTable.addCell(hCell);
            }

            if (tickets == null || tickets.isEmpty()) {
                PdfPCell emptyCell = new PdfPCell(new Phrase("No records found", bodyFont));
                emptyCell.setColspan(headers.length);
                emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                emptyCell.setPadding(10);
                emptyCell.setBorderColor(borderGray);
                mainTable.addCell(emptyCell);
            } else {
                DateTimeFormatter dateOnly = DateTimeFormatter.ofPattern("yyyy-MM-dd");

                for (VendorTicket t : tickets) {
                    mainTable.addCell(createBodyCell(t.getTicketNo(), bodyFont, Element.ALIGN_LEFT, borderGray));
                    mainTable.addCell(createBodyCell(t.getVendor() != null ? t.getVendor().getUsername() : "", bodyFont,
                            Element.ALIGN_LEFT, borderGray));
                    mainTable.addCell(createBodyCell(t.getClient() != null ? t.getClient().getCompanyName() : "",
                            bodyFont, Element.ALIGN_LEFT, borderGray));
                    mainTable.addCell(createBodyCell(t.getPoNumber(), bodyFont, Element.ALIGN_CENTER, borderGray));
                    mainTable.addCell(createBodyCell(t.getInvoiceNo(), bodyFont, Element.ALIGN_CENTER, borderGray));
                    mainTable.addCell(createBodyCell(FormatUtils.formatCurrency(t.getAmount(), t.getCurrency()),
                            bodyFont, Element.ALIGN_RIGHT, borderGray));
                    mainTable.addCell(createBodyCell(t.getStatusRequest() != null ? t.getStatusRequest().name() : "",
                            bodyFont, Element.ALIGN_CENTER, borderGray));
                    mainTable.addCell(createBodyCell(t.getCreatedAt() != null ? t.getCreatedAt().format(dateOnly) : "",
                            bodyFont, Element.ALIGN_CENTER, borderGray));
                }
            }
            document.add(mainTable);

            // Footer info
            Paragraph footerInfo = new Paragraph("Generated by InvoiceHub", regularMuted);
            footerInfo.setAlignment(Element.ALIGN_CENTER);
            document.add(footerInfo);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new RuntimeException("Error during PDF report creation", e);
        }
    }

    private PdfPCell createBodyCell(String text, Font font, int align, Color border) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(border);
        cell.setPadding(4);
        return cell;
    }
}
