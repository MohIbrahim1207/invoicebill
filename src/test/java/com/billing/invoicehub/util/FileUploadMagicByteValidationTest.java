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
}
