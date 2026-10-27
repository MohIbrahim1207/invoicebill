package com.billing.invoicehub.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUploadMagicByteValidationTest {

    @Test
    @DisplayName("An executable (.exe) file renamed to .pdf must be rejected by magic-byte inspection")
    void exeDisguisedAsPdfIsRejected() {
        // Windows DOS/PE executable header starts with MZ
        byte[] exeContent = "MZ\u0090\u0000\u0003\u0000\u0000\u0000\u0004\u0000\u0000\u0000\u00FF\u00FF\u0000\u0000".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "invoiceFile",
                "malicious.pdf",
                "application/pdf",
                exeContent
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                FileValidationUtil.validateFile(file)
        );
        assertTrue(ex.getMessage().contains("File content does not match allowed types")
                        || ex.getMessage().contains("File content type is not supported")
                        || ex.getMessage().contains("File type not allowed"),
                "Expected rejection message, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("An HTML script file renamed to .pdf must be rejected by magic-byte inspection")
    void htmlDisguisedAsPdfIsRejected() {
        byte[] htmlContent = "<!DOCTYPE html><html><head><title>Test</title></head><body><script>alert(1)</script></body></html>".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "invoiceFile",
                "fake.pdf",
                "application/pdf",
                htmlContent
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                FileValidationUtil.validateFile(file)
        );
        assertTrue(ex.getMessage().contains("File content does not match allowed types")
                        || ex.getMessage().contains("File content type is not supported")
                        || ex.getMessage().contains("File type not allowed"),
                "Expected rejection message, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("A genuine PDF file is accepted")
    void genuinePdfIsAccepted() {
        byte[] pdfContent = "%PDF-1.5\n%\u00E2\u00E3\u00CF\u00D3\n1 0 obj\n<<\n/Type /Catalog\n>>\nendobj\ntrailer\n<<\n>>\n%%EOF".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "invoiceFile",
                "valid.pdf",
                "application/pdf",
                pdfContent
        );

        assertDoesNotThrow(() -> FileValidationUtil.validateFile(file));
    }

    @Test
    @DisplayName("A genuine PNG image is accepted")
    void genuinePngIsAccepted() {
        // Standard PNG 8-byte header: 89 50 4E 47 0D 0A 1A 0A followed by IHDR chunk
        byte[] pngContent = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89
        };
        MockMultipartFile file = new MockMultipartFile(
                "companyDocument",
                "logo.png",
                "image/png",
                pngContent
        );

        assertDoesNotThrow(() -> FileValidationUtil.validateFile(file));
    }

    @Test
    @DisplayName("A genuine JPEG image is accepted")
    void genuineJpegIsAccepted() {
        // Standard JPEG SOI marker FF D8 FF E0
        byte[] jpegContent = new byte[] {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
                0x01, 0x01, 0x00, 0x60, 0x00, 0x60, 0x00, 0x00,
                (byte) 0xFF, (byte) 0xD9
        };
        MockMultipartFile file = new MockMultipartFile(
                "gstDocument",
                "cert.jpg",
                "image/jpeg",
                jpegContent
        );

        assertDoesNotThrow(() -> FileValidationUtil.validateFile(file));
    }

    @Test
    @DisplayName("A file exceeding 10 MB limit must be rejected")
    void fileExceeding10MbIsRejected() {
        byte[] oversizedContent = new byte[(int) (10 * 1024 * 1024 + 1)]; // 10MB + 1 byte
        MockMultipartFile file = new MockMultipartFile(
                "invoiceFile",
                "large.pdf",
                "application/pdf",
                oversizedContent
        );

        FileValidationUtil.FileValidationException ex = assertThrows(
                FileValidationUtil.FileValidationException.class,
                () -> FileValidationUtil.validateFile(file, "invoiceFile")
        );
        assertTrue(ex.getMessage().contains("File size exceeds the maximum allowed limit of 10 MB"),
                "Expected size limit message, got: " + ex.getMessage());
        org.junit.jupiter.api.Assertions.assertEquals("invoiceFile", ex.getFieldName());
    }

    @Test
    @DisplayName("Error messages are localized into Indonesian when locale is 'id'")
    void localizedIndonesianErrorMessage() {
        try {
            org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.forLanguageTag("id"));
            byte[] htmlContent = "<html><body>bad</body></html>".getBytes();
            MockMultipartFile file = new MockMultipartFile(
                    "gstDocument",
                    "disguised.pdf",
                    "application/pdf",
                    htmlContent
            );

            FileValidationUtil.FileValidationException ex = assertThrows(
                    FileValidationUtil.FileValidationException.class,
                    () -> FileValidationUtil.validateFile(file, "gstDocument")
            );
            assertTrue(ex.getMessage().contains("Konten file tidak sesuai dengan format yang diizinkan"),
                    "Expected Indonesian rejection message, got: " + ex.getMessage());
            org.junit.jupiter.api.Assertions.assertEquals("gstDocument", ex.getFieldName());
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    @DisplayName("A real .xlsx workbook is accepted for invoice/ticket uploads")
    void realXlsxIsAccepted() throws Exception {
        byte[] content = createRealXlsx();
        MockMultipartFile file = new MockMultipartFile(
                "invoiceFile",
                "invoice.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );
        assertDoesNotThrow(() -> FileValidationUtil.validateFile(file));
    }

    @Test
    @DisplayName("A real .docx document is accepted for invoice/ticket uploads")
    void realDocxIsAccepted() throws Exception {
        byte[] content = createRealDocx();
        MockMultipartFile file = new MockMultipartFile(
                "supportingDocument",
                "contract.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                content
        );
        assertDoesNotThrow(() -> FileValidationUtil.validateFile(file));
    }

    @Test
    @DisplayName("A real .webp image is accepted for registration documents")
    void realWebpIsAcceptedForRegistration() {
        byte[] content = createRealWebp();
        MockMultipartFile file = new MockMultipartFile(
                "gstDocument",
                "tax_card.webp",
                "image/webp",
                content
        );
        assertDoesNotThrow(() -> FileValidationUtil.validateRegistrationFile(file, "gstDocument"));
    }

    @Test
    @DisplayName("A macro-enabled (.xlsm) file is rejected")
    void macroEnabledXlsmIsRejected() throws Exception {
        byte[] content = createMacroEnabledXlsm();
        MockMultipartFile file = new MockMultipartFile(
                "invoiceFile",
                "payroll.xlsm",
                "application/vnd.ms-excel.sheet.macroenabled.12",
                content
        );
        assertThrows(IllegalArgumentException.class, () -> FileValidationUtil.validateFile(file));
    }

    @Test
    @DisplayName("An old legacy binary Excel file (.xls) is rejected")
    void oldXlsIsRejected() throws Exception {
        byte[] content = createOldXls();
        MockMultipartFile file = new MockMultipartFile(
                "invoiceFile",
                "legacy_invoice.xls",
                "application/vnd.ms-excel",
                content
        );
        assertThrows(IllegalArgumentException.class, () -> FileValidationUtil.validateFile(file));
    }

    @Test
    @DisplayName("A generic .zip file renamed to .xlsx is rejected")
    void zipRenamedToXlsxIsRejected() throws Exception {
        byte[] content = createRenamedZip();
        MockMultipartFile file = new MockMultipartFile(
                "invoiceFile",
                "disguised_archive.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );
        assertThrows(IllegalArgumentException.class, () -> FileValidationUtil.validateFile(file));
    }

    private byte[] createRealXlsx() throws Exception {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            wb.createSheet("InvoiceSheet").createRow(0).createCell(0).setCellValue("Invoice Total");
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createRealDocx() throws Exception {
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Invoice Agreement Document");
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createOldXls() throws Exception {
        try (org.apache.poi.hssf.usermodel.HSSFWorkbook wb = new org.apache.poi.hssf.usermodel.HSSFWorkbook();
             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            wb.createSheet("OldSheet").createRow(0).createCell(0).setCellValue("Legacy Excel");
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createMacroEnabledXlsm() throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            zos.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.ms-excel.sheet.macroenabled.main+xml\"/></Types>".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("xl/workbook.xml"));
            zos.write("<workbook/>".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("xl/vbaProject.bin"));
            zos.write(new byte[]{0x4D, 0x53, 0x43, 0x46});
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createRenamedZip() throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("dummy.txt"));
            zos.write("This is a plain text file inside a normal zip".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createRealWebp() {
        return new byte[] {
                'R', 'I', 'F', 'F',
                0x18, 0x00, 0x00, 0x00,
                'W', 'E', 'B', 'P',
                'V', 'P', '8', ' ',
                0x0c, 0x00, 0x00, 0x00,
                (byte) 0xd5, (byte) 0x00, (byte) 0x00, (byte) 0x9d, 0x01, 0x2a,
                0x01, 0x00, 0x01, 0x00, 0x00, 0x00
        };
    }
}
