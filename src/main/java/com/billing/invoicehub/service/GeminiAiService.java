package com.billing.invoicehub.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiAiService implements AIProvider {

    private static final Logger logger = LoggerFactory.getLogger(GeminiAiService.class);

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String modelName;

    private final HttpClient httpClient;
    private final Gson gson;

    public GeminiAiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.gson = new Gson();
    }

    @Override
    public String extractQuotation(String pdfText) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API Key is not configured.");
        }

        if (pdfText == null || pdfText.isBlank()) {
            throw new IllegalArgumentException("PDF text is empty, cannot extract details.");
        }

        String systemPrompt = "You are an expert system that extracts information from supplier quotations and formats it into a highly structured JSON object.\n" +
                "You must output a single JSON object. The keys and structure MUST match the following format:\n" +
                "{\n" +
                "  \"supplierName\": \"Name of the supplier company\",\n" +
                "  \"customerName\": \"Name of the customer/recipient company\",\n" +
                "  \"attention\": \"Contact person or department specified in the attention line\",\n" +
                "  \"quotationNumber\": \"Quotation reference or quote number\",\n" +
                "  \"date\": \"Quotation date in YYYY-MM-DD format (or string as written)\",\n" +
                "  \"reference\": \"Reference number or code of the quotation\",\n" +
                "  \"subject\": \"Subject of the quotation\",\n" +
                "  \"machineName\": \"Name of the main machine or product\",\n" +
                "  \"machineModel\": \"Model number/name of the machine\",\n" +
                "  \"capacity\": \"Capacity specification of the machine (e.g. 500 L, 50 HP)\",\n" +
                "  \"product\": \"Product classification (e.g. Lab Homogeniser / Research)\",\n" +
                "  \"operatingTemperature\": \"Operating temperature specification\",\n" +
                "  \"operatingPressure\": \"Operating pressure specification\",\n" +
                "  \"requiredFeedPressure\": \"Required feed pressure specification\",\n" +
                "  \"plungerDiameter\": \"Plunger diameter specification\",\n" +
                "  \"powerConsumption\": \"Power consumption specification\",\n" +
                "  \"eccentricShaftSpeed\": \"Eccentric shaft speed specification\",\n" +
                "  \"deliveryDetails\": \"Detailed list of what is included in delivery (e.g. prewired, testing, crating)\",\n" +
                "  \"dimensionsWeight\": \"Dimensions and weight specification\",\n" +
                "  \"pricesDescription\": \"Prices terms description (e.g. Incl. Machine, Excl. Extra options...)\",\n" +
                "  \"discountDescription\": \"Discount terms description (e.g. 15% to dealer...)\",\n" +
                "  \"deliveryTerms\": \"Delivery terms (e.g. EXW Hilversum, the Netherlands)\",\n" +
                "  \"termsConditions\": \"General supply conditions references (e.g. Bos Homogenisers B.V. Supply Conditions July 2018 apply)\",\n" +
                "  \"technicalSpecifications\": \"Detailed list or summary of technical specifications as written in the quote\",\n" +
                "  \"currency\": \"Currency code (e.g., USD, EUR, INR, IDR)\",\n" +
                "  \"subtotal\": 1000.00,\n" +
                "  \"discount\": 50.00,\n" +
                "  \"tax\": 100.00,\n" +
                "  \"totalAmount\": 1050.00,\n" +
                "  \"deliveryTime\": \"Delivery lead time (e.g. 4-6 weeks or 10-12 weeks)\",\n" +
                "  \"warranty\": \"Warranty terms (e.g. 12 months or 2 year...)\",\n" +
                "  \"paymentTerms\": \"Payment terms (e.g. 50% advance, 50% against delivery or 30% with order, 70% before shipment)\",\n" +
                "  \"validity\": \"Validity date or period of the quote\",\n" +
                "  \"notes\": \"Any extra notes, conditions or remarks\",\n" +
                "  \"lineItems\": [\n" +
                "    {\n" +
                "      \"itemName\": \"Item name or model\",\n" +
                "      \"description\": \"Item description\",\n" +
                "      \"quantity\": 1.0,\n" +
                "      \"unitPrice\": 1000.00,\n" +
                "      \"totalPrice\": 1000.00\n" +
                "    }\n" +
                "  ],\n" +
                "  \"optionalItems\": [\n" +
                "    {\n" +
                "      \"itemName\": \"Optional item name\",\n" +
                "      \"description\": \"Optional item description\",\n" +
                "      \"price\": 200.00\n" +
                "    }\n" +
                "  ]\n" +
                "}\n" +
                "If any field is missing from the quotation text, set its value to null (or empty list for arrays). Do not invent information.\n" +
                "CRITICAL REQUIREMENT: Extract all text fields verbatim from the source document. Do not paraphrase, summarize, reword, or omit any content. Preserve exact wording, numbers, units, decimal formatting, and punctuation.\n" +
                "Extract all line items and optional items from the pricing/options table without filtering or omitting any row (including items marked 'Included'). Every single item must be copied verbatim.\n" +
                "Do NOT extract or recreate any logos, headers, or signatures. The AI should only extract the raw quotation text, product specs, and pricing details.\n" +
                "Do NOT extract signature blocks, sign-off text (such as 'Best regards', 'Yours Sincerely', 'PT. Flow Force Engineering', 'Umapathi', 'General Manager', or any signatory names/titles/closings) into 'notes', 'termsConditions', 'deliveryDetails', or ANY other fields. These closing texts are static parts of the template and must be completely ignored during extraction.";

        // Build Gemini API payload
        Map<String, Object> requestMap = new HashMap<>();
        
        List<Map<String, Object>> contentsList = new ArrayList<>();
        Map<String, Object> contentMap = new HashMap<>();
        List<Map<String, String>> partsList = new ArrayList<>();
        Map<String, String> partMap = new HashMap<>();
        partMap.put("text", "Extract details from the following quotation text:\n\n" + pdfText);
        partsList.add(partMap);
        contentMap.put("parts", partsList);
        contentsList.add(contentMap);
        requestMap.put("contents", contentsList);

        Map<String, Object> systemInstructionMap = new HashMap<>();
        List<Map<String, String>> systemInstructionParts = new ArrayList<>();
        Map<String, String> systemInstructionPart = new HashMap<>();
        systemInstructionPart.put("text", systemPrompt);
        systemInstructionParts.add(systemInstructionPart);
        systemInstructionMap.put("parts", systemInstructionParts);
        requestMap.put("systemInstruction", systemInstructionMap);

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("temperature", 0.1);
        requestMap.put("generationConfig", generationConfig);

        String requestBody = gson.toJson(requestMap);

        // Rest endpoint construction
        String actualModel = (modelName == null || modelName.isBlank()) ? "gemini-2.5-flash" : modelName;
        String endpoint = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", 
                actualModel, 
                apiKey);

        int maxRetries = 3;
        long backoffDelayMs = 1000;
        IOException lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .timeout(Duration.ofSeconds(30))
                        .build();

                logger.info("Querying Google Gemini API (Attempt {}/{}) for model {}", attempt, maxRetries, actualModel);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                int statusCode = response.statusCode();
                if (statusCode == 200) {
                    String responseBody = response.body();
                    JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();
                    String extractedText = responseJson.getAsJsonArray("candidates")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("content")
                            .getAsJsonArray("parts")
                            .get(0).getAsJsonObject()
                            .get("text").getAsString();

                    // JSON validity check
                    try {
                        JsonParser.parseString(extractedText);
                        logger.info("Structured details extracted successfully from Gemini.");
                        return extractedText;
                    } catch (Exception ex) {
                        logger.warn("Gemini returned invalid JSON structure on attempt {}: {}", attempt, ex.getMessage());
                        if (attempt == maxRetries) {
                            throw new IOException("Gemini returned invalid JSON structure: " + extractedText, ex);
                        }
                    }
                } else if (statusCode == 429) {
                    logger.warn("Gemini API rate limit exceeded (HTTP 429) on attempt {}.", attempt);
                    if (attempt == maxRetries) {
                        throw new IOException("Gemini API rate limit exceeded. The AI service is currently busy. Please try again shortly.");
                    }
                } else if (statusCode == 403 || statusCode == 401) {
                    throw new IOException("Invalid Google Gemini API key or unauthorized access (HTTP " + statusCode + "). Please verify settings.");
                } else if (statusCode >= 500) {
                    logger.warn("Gemini server error (HTTP {}) on attempt {}.", statusCode, attempt);
                    if (attempt == maxRetries) {
                        throw new IOException("Google Gemini AI service is temporarily unavailable (HTTP " + statusCode + "). Please try again later.");
                    }
                } else {
                    throw new IOException("Gemini API call failed with HTTP status: " + statusCode + ". Body: " + response.body());
                }
            } catch (IOException e) {
                logger.warn("IOException occurred during Gemini request on attempt {}: {}", attempt, e.getMessage());
                lastException = e;
            }

            if (attempt < maxRetries) {
                logger.info("Retrying in {}ms...", backoffDelayMs);
                Thread.sleep(backoffDelayMs);
                backoffDelayMs *= 2;
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        throw new IOException("Failed to extract quotation from Google Gemini API after " + maxRetries + " attempts.");
    }
}
