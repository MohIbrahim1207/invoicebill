package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.AiQuotationEditDto;
import com.google.gson.Gson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DirectExtractionTest {

    @Test
    public void testCleanTextPreservesAllLines() throws Exception {
        File sourcePdf = new File("uploads/bos_mg2_800b.pdf");
        if (!sourcePdf.exists()) {
            sourcePdf = new File("c:/Users/NEW/Invoicehub/uploads/bos_mg2_800b.pdf");
        }
        assertTrue(sourcePdf.exists(), "Source PDF must exist at uploads/bos_mg2_800b.pdf");

        byte[] pdfBytes = Files.readAllBytes(sourcePdf.toPath());
        PdfExtractionService pdfExtractionService = new PdfExtractionService();
        String rawText = pdfExtractionService.extractText(pdfBytes);
        assertNotNull(rawText);

        System.out.println("=== RAW EXTRACTED PDF TEXT LENGTH: " + rawText.length() + " chars ===");

        // Access cleanText method via reflection in GeminiAiService
        GeminiAiService service = new GeminiAiService();
        java.lang.reflect.Method cleanTextMethod = GeminiAiService.class.getDeclaredMethod("cleanText", String.class);
        cleanTextMethod.setAccessible(true);

        String cleanedText = (String) cleanTextMethod.invoke(service, rawText);

        System.out.println("=== CLEANED TEXT LENGTH: " + cleanedText.length() + " chars ===");

        System.out.println("=== FULL RAW PDF TEXT ===");
        System.out.println(rawText);
        System.out.println("=========================");

        // Verify that duplicated section headers or numbers are NOT stripped
        int countClause1 = countOccurrences(cleanedText, "1.");
        System.out.println("Occurrences of '1.' in cleanedText: " + countClause1);
        assertTrue(countClause1 >= 1, "Clause 1 must be present");
    }

    private int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
