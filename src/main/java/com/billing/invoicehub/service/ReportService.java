package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.WeeklyTicketReportDto;
import com.billing.invoicehub.entity.AppUser;
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
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final VendorTicketRepository vendorTicketRepository;
    private final VendorRegistrationService vendorRegistrationService;

    public ReportService(VendorTicketRepository vendorTicketRepository,
                         VendorRegistrationService vendorRegistrationService) {
        this.vendorTicketRepository = vendorTicketRepository;
        this.vendorRegistrationService = vendorRegistrationService;
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

    public List<AppUser> getFilteredVendors(LocalDate startDate, LocalDate endDate, String status, String search) {
        List<AppUser> allVendors = vendorRegistrationService.listVendors();
        return allVendors.stream()
                .filter(v -> {
                    if (startDate != null) {
                        if (v.getRegistrationDate() == null || v.getRegistrationDate().toLocalDate().isBefore(startDate)) {
                            return false;
                        }
                    }
                    if (endDate != null) {
                        if (v.getRegistrationDate() == null || v.getRegistrationDate().toLocalDate().isAfter(endDate)) {
                            return false;
                        }
                    }
                    if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status.trim())) {
                        String vStatus = vendorRegistrationService.getVendorStatus(v);
                        if (!vStatus.equalsIgnoreCase(status.trim())) {
                            return false;
                        }
                    }
                    if (search != null && !search.isBlank()) {
                        String q = search.trim().toLowerCase();
                        boolean matchCompany = v.getCompanyName() != null && v.getCompanyName().toLowerCase().contains(q);
                        boolean matchName = v.getFullName() != null && v.getFullName().toLowerCase().contains(q);
                        boolean matchEmail = v.getEmail() != null && v.getEmail().toLowerCase().contains(q);
                        boolean matchGst = v.getGstNumber() != null && v.getGstNumber().toLowerCase().contains(q);
                        boolean matchCode = v.getVendorCode() != null && v.getVendorCode().toLowerCase().contains(q);
                        if (!matchCompany && !matchName && !matchEmail && !matchGst && !matchCode) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> getVendorPreview(LocalDate startDate, LocalDate endDate, String status, String search) {
        List<AppUser> vendors = getFilteredVendors(startDate, endDate, status, search);
        long total = vendors.size();
        long verified = 0;
        long pending = 0;
        long rejected = 0;

        for (AppUser v : vendors) {
            String s = vendorRegistrationService.getVendorStatus(v);
            if ("Verified".equalsIgnoreCase(s)) verified++;
            else if ("Pending".equalsIgnoreCase(s)) pending++;
            else if ("Rejected".equalsIgnoreCase(s)) rejected++;
        }

        Map<String, Object> res = new HashMap<>();
        res.put("total", total);
        res.put("verified", verified);
        res.put("pending", pending);
        res.put("rejected", rejected);
        return res;
    }

    public byte[] generateVendorExcelReport(List<AppUser> vendors, Map<String, String> appliedFilters) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Color headerColor = new Color(20, 74, 122);
            org.apache.poi.xssf.usermodel.XSSFColor xssfHeaderColor = new org.apache.poi.xssf.usermodel.XSSFColor(headerColor, null);

            // Title Font & Style
            org.apache.poi.xssf.usermodel.XSSFFont titleFont = (org.apache.poi.xssf.usermodel.XSSFFont) workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleFont.setColor(xssfHeaderColor);

            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);

            // Meta/Subtitle Font & Style
            org.apache.poi.ss.usermodel.Font metaFont = workbook.createFont();
            metaFont.setFontHeightInPoints((short) 9);
            metaFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            metaFont.setItalic(true);

            CellStyle metaStyle = workbook.createCellStyle();
            metaStyle.setFont(metaFont);

            // Header Font & Style
            org.apache.poi.xssf.usermodel.XSSFFont headerFont = (org.apache.poi.xssf.usermodel.XSSFFont) workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(xssfHeaderColor);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Formats
            DataFormat dataFormat = workbook.createDataFormat();
            short textFormat = dataFormat.getFormat("@");
            short integerFormat = dataFormat.getFormat("#,##0");
            short currencyFormat = dataFormat.getFormat("#,##0");

            // Text Styles
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(textFormat);
            textStyle.setBorderTop(BorderStyle.THIN);
            textStyle.setBorderBottom(BorderStyle.THIN);
            textStyle.setBorderLeft(BorderStyle.THIN);
            textStyle.setBorderRight(BorderStyle.THIN);
            textStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle textCenterStyle = workbook.createCellStyle();
            textCenterStyle.cloneStyleFrom(textStyle);
            textCenterStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle addressStyle = workbook.createCellStyle();
            addressStyle.cloneStyleFrom(textStyle);
            addressStyle.setWrapText(true);

            // Integer Style
            CellStyle intStyle = workbook.createCellStyle();
            intStyle.setDataFormat(integerFormat);
            intStyle.setBorderTop(BorderStyle.THIN);
            intStyle.setBorderBottom(BorderStyle.THIN);
            intStyle.setBorderLeft(BorderStyle.THIN);
            intStyle.setBorderRight(BorderStyle.THIN);
            intStyle.setAlignment(HorizontalAlignment.RIGHT);
            intStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Currency Style
            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setDataFormat(currencyFormat);
            currencyStyle.setBorderTop(BorderStyle.THIN);
            currencyStyle.setBorderBottom(BorderStyle.THIN);
            currencyStyle.setBorderLeft(BorderStyle.THIN);
            currencyStyle.setBorderRight(BorderStyle.THIN);
            currencyStyle.setAlignment(HorizontalAlignment.RIGHT);
            currencyStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Total Row Styles
            org.apache.poi.ss.usermodel.Font totalFont = workbook.createFont();
            totalFont.setBold(true);

            CellStyle totalTextBoldStyle = workbook.createCellStyle();
            totalTextBoldStyle.setFont(totalFont);
            totalTextBoldStyle.setBorderTop(BorderStyle.THIN);
            totalTextBoldStyle.setBorderBottom(BorderStyle.DOUBLE);
            totalTextBoldStyle.setBorderLeft(BorderStyle.THIN);
            totalTextBoldStyle.setBorderRight(BorderStyle.THIN);
            totalTextBoldStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle totalNumericBoldStyle = workbook.createCellStyle();
            totalNumericBoldStyle.setFont(totalFont);
            totalNumericBoldStyle.setDataFormat(integerFormat);
            totalNumericBoldStyle.setBorderTop(BorderStyle.THIN);
            totalNumericBoldStyle.setBorderBottom(BorderStyle.DOUBLE);
            totalNumericBoldStyle.setBorderLeft(BorderStyle.THIN);
            totalNumericBoldStyle.setBorderRight(BorderStyle.THIN);
            totalNumericBoldStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalNumericBoldStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle totalCurrencyBoldStyle = workbook.createCellStyle();
            totalCurrencyBoldStyle.setFont(totalFont);
            totalCurrencyBoldStyle.setDataFormat(currencyFormat);
            totalCurrencyBoldStyle.setBorderTop(BorderStyle.THIN);
            totalCurrencyBoldStyle.setBorderBottom(BorderStyle.DOUBLE);
            totalCurrencyBoldStyle.setBorderLeft(BorderStyle.THIN);
            totalCurrencyBoldStyle.setBorderRight(BorderStyle.THIN);
            totalCurrencyBoldStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalCurrencyBoldStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // -------------------------------------------------------------
            // SHEET 1: Vendors
            // -------------------------------------------------------------
            Sheet sheet1 = workbook.createSheet("Vendors");

            // Title block
            Row titleRow1 = sheet1.createRow(0);
            Cell tCell1 = titleRow1.createCell(0);
            tCell1.setCellValue("Vendor Report");
            tCell1.setCellStyle(titleStyle);

            Row genRow1 = sheet1.createRow(1);
            Cell gCell1 = genRow1.createCell(0);
            gCell1.setCellValue("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")));
            gCell1.setCellStyle(metaStyle);

            Row filterRow1 = sheet1.createRow(2);
            Cell fCell1 = filterRow1.createCell(0);
            fCell1.setCellValue("Filters: Start Date: " + appliedFilters.getOrDefault("Start Date", "ALL")
                    + " | End Date: " + appliedFilters.getOrDefault("End Date", "ALL")
                    + " | Status: " + appliedFilters.getOrDefault("Status", "ALL")
                    + " | Search: " + appliedFilters.getOrDefault("Search", "ALL"));
            fCell1.setCellStyle(metaStyle);

            String[] s1Headers = new String[]{
                    "No.", "Company Name", "Vendor Code", "Full Name", "Email", "Phone",
                    "GST Number / NPWP", "Address", "Registration Date", "Verification Status",
                    "Number of Documents Uploaded"
            };

            Row headerRow1 = sheet1.createRow(4);
            headerRow1.setHeightInPoints(24);
            for (int i = 0; i < s1Headers.length; i++) {
                Cell c = headerRow1.createCell(i);
                c.setCellValue(s1Headers[i]);
                c.setCellStyle(headerStyle);
            }

            int s1RowIdx = 5;
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
            for (int i = 0; i < vendors.size(); i++) {
                AppUser v = vendors.get(i);
                Row row = sheet1.createRow(s1RowIdx++);

                Cell c0 = row.createCell(0);
                c0.setCellValue(i + 1);
                c0.setCellStyle(textCenterStyle);

                Cell c1 = row.createCell(1, CellType.STRING);
                c1.setCellValue(v.getCompanyName() != null ? v.getCompanyName() : "-");
                c1.setCellStyle(textStyle);

                Cell c2 = row.createCell(2, CellType.STRING);
                c2.setCellValue(v.getVendorCode() != null ? v.getVendorCode() : "-");
                c2.setCellStyle(textCenterStyle);

                Cell c3 = row.createCell(3, CellType.STRING);
                c3.setCellValue(v.getFullName() != null ? v.getFullName() : "-");
                c3.setCellStyle(textStyle);

                Cell c4 = row.createCell(4, CellType.STRING);
                c4.setCellValue(v.getEmail() != null ? v.getEmail() : "-");
                c4.setCellStyle(textStyle);

                Cell c5 = row.createCell(5, CellType.STRING);
                c5.setCellValue(v.getPhone() != null ? v.getPhone() : "-");
                c5.setCellStyle(textCenterStyle);

                Cell c6 = row.createCell(6, CellType.STRING);
                c6.setCellValue(v.getGstNumber() != null ? v.getGstNumber() : "-");
                c6.setCellStyle(textCenterStyle);

                Cell c7 = row.createCell(7, CellType.STRING);
                c7.setCellValue(v.getAddress() != null ? v.getAddress() : "-");
                c7.setCellStyle(addressStyle);

                Cell c8 = row.createCell(8, CellType.STRING);
                c8.setCellValue(v.getRegistrationDate() != null ? v.getRegistrationDate().format(dtf) : "-");
                c8.setCellStyle(textCenterStyle);

                Cell c9 = row.createCell(9, CellType.STRING);
                c9.setCellValue(vendorRegistrationService.getVendorStatus(v));
                c9.setCellStyle(textCenterStyle);

                int docCount = 0;
                if (v.getGstDocumentUrl() != null && !v.getGstDocumentUrl().isBlank()) docCount++;
                if (v.getCompanyDocumentUrl() != null && !v.getCompanyDocumentUrl().isBlank()) docCount++;
                if (v.getSupportingDocumentUrl() != null && !v.getSupportingDocumentUrl().isBlank()) docCount++;

                Cell c10 = row.createCell(10, CellType.NUMERIC);
                c10.setCellValue(docCount);
                c10.setCellStyle(intStyle);
            }

            sheet1.createFreezePane(0, 5);
            int lastRow1 = Math.max(4, s1RowIdx - 1);
            sheet1.setAutoFilter(new CellRangeAddress(4, lastRow1, 0, s1Headers.length - 1));

            for (int i = 0; i < s1Headers.length; i++) {
                sheet1.autoSizeColumn(i);
                int w = sheet1.getColumnWidth(i);
                if (w > 50 * 256) {
                    sheet1.setColumnWidth(i, 50 * 256);
                } else if (w < 12 * 256) {
                    sheet1.setColumnWidth(i, 12 * 256);
                }
            }

            // -------------------------------------------------------------
            // SHEET 2: Vendor Ticket Summary
            // -------------------------------------------------------------
            Sheet sheet2 = workbook.createSheet("Vendor Ticket Summary");

            Row titleRow2 = sheet2.createRow(0);
            Cell tCell2 = titleRow2.createCell(0);
            tCell2.setCellValue("Vendor Ticket Summary");
            tCell2.setCellStyle(titleStyle);

            Row genRow2 = sheet2.createRow(1);
            Cell gCell2 = genRow2.createCell(0);
            gCell2.setCellValue("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")));
            gCell2.setCellStyle(metaStyle);

            Row filterRow2 = sheet2.createRow(2);
            Cell fCell2 = filterRow2.createCell(0);
            fCell2.setCellValue("Filters: Start Date: " + appliedFilters.getOrDefault("Start Date", "ALL")
                    + " | End Date: " + appliedFilters.getOrDefault("End Date", "ALL")
                    + " | Status: " + appliedFilters.getOrDefault("Status", "ALL")
                    + " | Search: " + appliedFilters.getOrDefault("Search", "ALL"));
            fCell2.setCellStyle(metaStyle);

            String[] s2Headers = new String[]{
                    "No.", "Company Name", "Total Tickets", "Pending", "In Progress",
                    "Paid", "Rejected", "Cancelled", "Total Invoice Amount"
            };

            Row headerRow2 = sheet2.createRow(4);
            headerRow2.setHeightInPoints(24);
            for (int i = 0; i < s2Headers.length; i++) {
                Cell c = headerRow2.createCell(i);
                c.setCellValue(s2Headers[i]);
                c.setCellStyle(headerStyle);
            }

            List<VendorTicket> allTickets = vendorTicketRepository.findAllWithVendor();
            Map<Long, List<VendorTicket>> ticketsByVendorId = new HashMap<>();
            List<VendorTicket> unassignedTickets = new ArrayList<>();

            for (VendorTicket t : allTickets) {
                if (t.getVendor() != null && t.getVendor().getId() != null) {
                    ticketsByVendorId.computeIfAbsent(t.getVendor().getId(), k -> new ArrayList<>()).add(t);
                } else {
                    unassignedTickets.add(t);
                }
            }

            Set<Long> displayedVendorIds = vendors.stream().map(AppUser::getId).filter(Objects::nonNull).collect(Collectors.toSet());
            boolean isFiltered = "true".equalsIgnoreCase(appliedFilters.getOrDefault("isFiltered", "false"));

            // If no filters are applied, tickets for vendors not in the displayed vendor list belong to unassigned
            if (!isFiltered) {
                for (Map.Entry<Long, List<VendorTicket>> entry : ticketsByVendorId.entrySet()) {
                    if (!displayedVendorIds.contains(entry.getKey())) {
                        unassignedTickets.addAll(entry.getValue());
                    }
                }
            }

            long sumTotal = 0;
            long sumPending = 0;
            long sumInProgress = 0;
            long sumPaid = 0;
            long sumRejected = 0;
            long sumCancelled = 0;
            BigDecimal sumAmount = BigDecimal.ZERO;

            int s2RowIdx = 5;
            int s2No = 1;

            for (AppUser v : vendors) {
                List<VendorTicket> vTickets = ticketsByVendorId.getOrDefault(v.getId(), Collections.emptyList());
                long vTotal = vTickets.size();
                long vPending = 0;
                long vInProgress = 0;
                long vPaid = 0;
                long vRejected = 0;
                long vCancelled = 0;
                BigDecimal vAmount = BigDecimal.ZERO;

                for (VendorTicket t : vTickets) {
                    if (t.getStatusRequest() != null) {
                        switch (t.getStatusRequest()) {
                            case OPEN -> vPending++;
                            case IN_PROGRESS -> vInProgress++;
                            case RESOLVED, PARTIALLY_PAID -> vPaid++;
                            case REVISE -> vRejected++;
                            case CANCEL -> vCancelled++;
                        }
                    }
                    BigDecimal amt = t.getTotal() != null ? t.getTotal() : (t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO);
                    vAmount = vAmount.add(amt);
                }

                sumTotal += vTotal;
                sumPending += vPending;
                sumInProgress += vInProgress;
                sumPaid += vPaid;
                sumRejected += vRejected;
                sumCancelled += vCancelled;
                sumAmount = sumAmount.add(vAmount);

                Row row = sheet2.createRow(s2RowIdx++);
                Cell c0 = row.createCell(0);
                c0.setCellValue(s2No++);
                c0.setCellStyle(textCenterStyle);

                Cell c1 = row.createCell(1, CellType.STRING);
                String compName = v.getCompanyName() != null ? v.getCompanyName() : (v.getUsername() != null ? v.getUsername() : "-");
                c1.setCellValue(compName);
                c1.setCellStyle(textStyle);

                Cell c2 = row.createCell(2, CellType.NUMERIC);
                c2.setCellValue(vTotal);
                c2.setCellStyle(intStyle);

                Cell c3 = row.createCell(3, CellType.NUMERIC);
                c3.setCellValue(vPending);
                c3.setCellStyle(intStyle);

                Cell c4 = row.createCell(4, CellType.NUMERIC);
                c4.setCellValue(vInProgress);
                c4.setCellStyle(intStyle);

                Cell c5 = row.createCell(5, CellType.NUMERIC);
                c5.setCellValue(vPaid);
                c5.setCellStyle(intStyle);

                Cell c6 = row.createCell(6, CellType.NUMERIC);
                c6.setCellValue(vRejected);
                c6.setCellStyle(intStyle);

                Cell c7 = row.createCell(7, CellType.NUMERIC);
                c7.setCellValue(vCancelled);
                c7.setCellStyle(intStyle);

                Cell c8 = row.createCell(8, CellType.NUMERIC);
                c8.setCellValue(vAmount.doubleValue());
                c8.setCellStyle(currencyStyle);
            }

            // Unassigned row if tickets exist
            if (!unassignedTickets.isEmpty()) {
                long uTotal = unassignedTickets.size();
                long uPending = 0;
                long uInProgress = 0;
                long uPaid = 0;
                long uRejected = 0;
                long uCancelled = 0;
                BigDecimal uAmount = BigDecimal.ZERO;

                for (VendorTicket t : unassignedTickets) {
                    if (t.getStatusRequest() != null) {
                        switch (t.getStatusRequest()) {
                            case OPEN -> uPending++;
                            case IN_PROGRESS -> uInProgress++;
                            case RESOLVED, PARTIALLY_PAID -> uPaid++;
                            case REVISE -> uRejected++;
                            case CANCEL -> uCancelled++;
                        }
                    }
                    BigDecimal amt = t.getTotal() != null ? t.getTotal() : (t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO);
                    uAmount = uAmount.add(amt);
                }

                sumTotal += uTotal;
                sumPending += uPending;
                sumInProgress += uInProgress;
                sumPaid += uPaid;
                sumRejected += uRejected;
                sumCancelled += uCancelled;
                sumAmount = sumAmount.add(uAmount);

                Row row = sheet2.createRow(s2RowIdx++);
                Cell c0 = row.createCell(0);
                c0.setCellValue(s2No++);
                c0.setCellStyle(textCenterStyle);

                Cell c1 = row.createCell(1, CellType.STRING);
                c1.setCellValue("Unassigned");
                c1.setCellStyle(textStyle);

                Cell c2 = row.createCell(2, CellType.NUMERIC);
                c2.setCellValue(uTotal);
                c2.setCellStyle(intStyle);

                Cell c3 = row.createCell(3, CellType.NUMERIC);
                c3.setCellValue(uPending);
                c3.setCellStyle(intStyle);

                Cell c4 = row.createCell(4, CellType.NUMERIC);
                c4.setCellValue(uInProgress);
                c4.setCellStyle(intStyle);

                Cell c5 = row.createCell(5, CellType.NUMERIC);
                c5.setCellValue(uPaid);
                c5.setCellStyle(intStyle);

                Cell c6 = row.createCell(6, CellType.NUMERIC);
                c6.setCellValue(uRejected);
                c6.setCellStyle(intStyle);

                Cell c7 = row.createCell(7, CellType.NUMERIC);
                c7.setCellValue(uCancelled);
                c7.setCellStyle(intStyle);

                Cell c8 = row.createCell(8, CellType.NUMERIC);
                c8.setCellValue(uAmount.doubleValue());
                c8.setCellStyle(currencyStyle);
            }

            // TOTAL row
            int totalRowIdx = s2RowIdx++;
            Row totalRow = sheet2.createRow(totalRowIdx);
            Cell tc0 = totalRow.createCell(0);
            tc0.setCellValue("");
            tc0.setCellStyle(totalTextBoldStyle);

            Cell tc1 = totalRow.createCell(1);
            tc1.setCellValue("TOTAL");
            tc1.setCellStyle(totalTextBoldStyle);

            Cell tc2 = totalRow.createCell(2, CellType.NUMERIC);
            tc2.setCellValue(sumTotal);
            tc2.setCellStyle(totalNumericBoldStyle);

            Cell tc3 = totalRow.createCell(3, CellType.NUMERIC);
            tc3.setCellValue(sumPending);
            tc3.setCellStyle(totalNumericBoldStyle);

            Cell tc4 = totalRow.createCell(4, CellType.NUMERIC);
            tc4.setCellValue(sumInProgress);
            tc4.setCellStyle(totalNumericBoldStyle);

            Cell tc5 = totalRow.createCell(5, CellType.NUMERIC);
            tc5.setCellValue(sumPaid);
            tc5.setCellStyle(totalNumericBoldStyle);

            Cell tc6 = totalRow.createCell(6, CellType.NUMERIC);
            tc6.setCellValue(sumRejected);
            tc6.setCellStyle(totalNumericBoldStyle);

            Cell tc7 = totalRow.createCell(7, CellType.NUMERIC);
            tc7.setCellValue(sumCancelled);
            tc7.setCellStyle(totalNumericBoldStyle);

            Cell tc8 = totalRow.createCell(8, CellType.NUMERIC);
            tc8.setCellValue(sumAmount.doubleValue());
            tc8.setCellStyle(totalCurrencyBoldStyle);

            sheet2.createFreezePane(0, 5);
            if (totalRowIdx > 5) {
                sheet2.setAutoFilter(new CellRangeAddress(4, totalRowIdx - 1, 0, s2Headers.length - 1));
            }

            for (int i = 0; i < s2Headers.length; i++) {
                sheet2.autoSizeColumn(i);
                int w = sheet2.getColumnWidth(i);
                if (w > 50 * 256) {
                    sheet2.setColumnWidth(i, 50 * 256);
                } else if (w < 12 * 256) {
                    sheet2.setColumnWidth(i, 12 * 256);
                }
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }
}
