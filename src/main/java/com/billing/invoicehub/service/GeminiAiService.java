package com.billing.invoicehub.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class GeminiAiService implements AIProvider {

    private static final Logger logger = LoggerFactory.getLogger(GeminiAiService.class);
    private static final int SPLIT_THRESHOLD_CHARS = 60000; // split if document text exceeds ~15k tokens
    private static final int CHUNK_SIZE_CHARS = 45000;

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-3-flash}")
    private String modelName;

    private final HttpClient httpClient;
    private final Gson gson;

    public GeminiAiService() {
        // Connect Timeout: 30 seconds
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.gson = new Gson();
    }

    @Override
    public String extractQuotation(String pdfText) throws IOException, InterruptedException {
        long totalStartTime = System.currentTimeMillis();

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API Key is not configured.");
        }

        if (pdfText == null || pdfText.isBlank()) {
            throw new IllegalArgumentException("PDF text is empty, cannot extract details.");
        }

        // Clean & Compress prompt text
        String cleanedText = cleanText(pdfText);

        String resultJson;
        if (cleanedText.length() > SPLIT_THRESHOLD_CHARS) {
            logger.info("PDF text size ({} chars) exceeds threshold. Splitting into logical chunks.", cleanedText.length());
            resultJson = extractInChunks(cleanedText);
        } else {
            resultJson = queryGemini(cleanedText, getFullSystemPrompt(), "application/json");
        }

        long totalDurationMs = System.currentTimeMillis() - totalStartTime;
        logger.info("Total request execution completed successfully in {} ms.", totalDurationMs);
        return resultJson;
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        // Normalize whitespace per line while preserving entire line sequence
        String[] lines = text.split("\\r?\\n");
        List<String> cleanedLines = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim().replaceAll("\\s+", " ");
            if (!trimmed.isEmpty()) {
                cleanedLines.add(trimmed);
            }
        }
        return String.join("\n", cleanedLines);
    }

    private String extractInChunks(String text) throws IOException, InterruptedException {
        List<String> chunks = splitText(text, CHUNK_SIZE_CHARS);
        logger.info("Split document into {} logical chunks for processing.", chunks.size());

        // Process first chunk for full structure
        String firstChunkResult = queryGemini(chunks.get(0), getFullSystemPrompt(), "application/json");
        JsonObject masterJson = JsonParser.parseString(firstChunkResult).getAsJsonObject();

        // Process remaining chunks for additional line/optional items and merge them
        for (int i = 1; i < chunks.size(); i++) {
            logger.info("Processing chunk {}/{} to extract supplementary line items.", i + 1, chunks.size());
            String chunkResult = queryGemini(chunks.get(i), getSupplementarySystemPrompt(), "application/json");
            try {
                JsonObject chunkJson = JsonParser.parseString(chunkResult).getAsJsonObject();
                mergeJsonArrays(masterJson, chunkJson, "lineItems");
                mergeJsonArrays(masterJson, chunkJson, "optionalItems");
            } catch (Exception e) {
                logger.warn("Failed to parse/merge JSON from chunk {}: {}", i + 1, e.getMessage());
            }
        }

        return gson.toJson(masterJson);
    }

    private List<String> splitText(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        int start = 0;
        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            // Try to find a logical boundary (like double newline or newline) near the end
            if (end < length) {
                int boundary = text.lastIndexOf("\n\n", end);
                if (boundary > start + (chunkSize / 2)) {
                    end = boundary + 2;
                } else {
                    boundary = text.lastIndexOf("\n", end);
                    if (boundary > start + (chunkSize / 2)) {
                        end = boundary + 1;
                    }
                }
            }
            chunks.add(text.substring(start, end).trim());
            start = end;
        }
        return chunks;
    }

    private void mergeJsonArrays(JsonObject target, JsonObject source, String key) {
        if (source.has(key) && source.get(key).isJsonArray()) {
            JsonArray sourceArray = source.getAsJsonArray(key);
            if (sourceArray.size() > 0) {
                if (!target.has(key) || !target.get(key).isJsonArray()) {
                    target.add(key, new JsonArray());
                }
                JsonArray targetArray = target.getAsJsonArray(key);
                for (JsonElement elem : sourceArray) {
                    targetArray.add(elem);
                }
            }
        }
    }

    private String queryGemini(String promptText, String systemInstruction, String mimeType) throws IOException, InterruptedException {
        // Estimate token counts (approx 4 chars per token for English text)
        int charCount = promptText.length();
        int tokenEstimate = charCount / 4;

        logger.info("Preparing Gemini Request - Prompt Size: {} characters, Estimated Tokens: {}", charCount, tokenEstimate);

        Map<String, Object> requestMap = new HashMap<>();
        List<Map<String, Object>> contentsList = new ArrayList<>();
        Map<String, Object> contentMap = new HashMap<>();
        List<Map<String, String>> partsList = new ArrayList<>();
        Map<String, String> partMap = new HashMap<>();
        partMap.put("text", "Extract details from the following quotation text:\n\n" + promptText);
        partsList.add(partMap);
        contentMap.put("parts", partsList);
        contentsList.add(contentMap);
        requestMap.put("contents", contentsList);

        Map<String, Object> systemInstructionMap = new HashMap<>();
        List<Map<String, String>> systemInstructionParts = new ArrayList<>();
        Map<String, String> systemInstructionPart = new HashMap<>();
        systemInstructionPart.put("text", systemInstruction);
        systemInstructionParts.add(systemInstructionPart);
        systemInstructionMap.put("parts", systemInstructionParts);
        requestMap.put("systemInstruction", systemInstructionMap);

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseMimeType", mimeType);
        generationConfig.put("temperature", 0.1);
        requestMap.put("generationConfig", generationConfig);

        String requestBody = gson.toJson(requestMap);

        String actualModel = (modelName == null || modelName.isBlank()) ? "gemini-3-flash" : modelName;
        String endpoint = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                actualModel,
                apiKey);

        int maxRetries = 3;
        long backoffDelayMs = 1000;
        IOException lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            long requestStartTime = System.currentTimeMillis();
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .header("x-goog-api-key", apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        // Request Timeout: 90 seconds (overall execution covers read timeout limits up to 180s)
                        .timeout(Duration.ofSeconds(90))
                        .build();

                logger.info("Querying Google Gemini API (Attempt {}/{}) for model {}", attempt, maxRetries, actualModel);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                long requestDurationMs = System.currentTimeMillis() - requestStartTime;
                logger.info("Gemini API HTTP request completed in {} ms.", requestDurationMs);

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

                    // Validation check
                    if ("application/json".equals(mimeType)) {
                        JsonParser.parseString(extractedText);
                    }
                    return extractedText;

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

    private String getFullSystemPrompt() {
        return
            // === ANTI-HALLUCINATION CONSTRAINT (applies globally to every field) ===
            "CRITICAL RULE — Non-Inference Constraint: Only extract information that is explicitly " +
            "present in the source document. Do NOT infer, generalize, estimate, or add any commercial " +
            "terms, discounts, delivery locations, dates, prices, or clauses that are not directly stated " +
            "in the input text. If a field or value is not present in the source, return null (or empty " +
            "array for lists) — NEVER fill in a plausible default, industry-standard value, or example.\n\n" +
            // === ROLE ===
            "You are an expert system that extracts information from supplier quotations and formats it into " +
            "a highly structured JSON object.\n" +
            "You must output a single JSON object. The keys and structure MUST match the following format:\n" +
            "{\n" +
            "  \"supplierName\": \"Name of the supplier company\",\n" +
            "  \"customerName\": \"Name of the customer/recipient company\",\n" +
            "  \"customerAddress\": \"Full address of the customer/recipient company, extracted verbatim if present. Return null if not present.\",\n" +
            "  \"attention\": \"Contact person or department specified in the attention line\",\n" +
            "  \"quotationNumber\": \"Quotation reference or quote number\",\n" +
            "  \"date\": \"Quotation date in YYYY-MM-DD format (or string as written)\",\n" +
            "  \"subject\": \"Subject of the quotation\",\n" +
            "  \"coverLetterBody\": \"ALL body paragraphs of the vendor's cover letter copied VERBATIM in full — " +
            "do NOT summarize, shorten, or omit any paragraph. Copy them exactly as written without any alterations, summarizes, or omissions. Wording changes will be handled later by the rendering application.\",\n" +
            "  \"specificationAttributes\": [\n" +
            "    { \"label\": \"Specification / Feature Label (e.g., SLA Resolution, Cloud Hosting, Storage Capacity, Fuel Surcharge)\", \"value\": \"Exact specification value (e.g., 4 Hours, AWS us-east-1, 1 TB, 12%)\" }\n" +
            "  ],\n" +
            "  \"customTables\": [\n" +
            "    {\n" +
            "      \"title\": \"Table Title or Heading if present (e.g., Service Level Milestones, Hardware Options)\",\n" +
            "      \"headers\": [\"Column 1 Header\", \"Column 2 Header\"],\n" +
            "      \"rows\": [{ \"cells\": [\"Cell 1\", \"Cell 2\"] }]\n" +
            "    }\n" +
            "  ],\n" +
            "  \"technicalSpecifications\": \"Complete, verbatim technical specifications or scope of work section if formatted as block text. Return null if not present.\",\n" +
            "  \"pricesDescription\": \"Prices terms description (e.g. Incl. Machine, Excl. Extra options...)\",\n" +
            "  \"discountDescription\": \"Discount terms description — only if explicitly stated in the source. Return null if not present.\",\n" +
            "  \"deliveryTerms\": \"Delivery terms — only if explicitly stated in the source (e.g. EXW Hilversum). Return null if not present.\",\n" +
            "  \"termsConditions\": \"Legacy summary field — include only if no numbered T&C clauses are present.\",\n" +
            "  \"termsAndConditionsList\": [\n" +
            "    {\n" +
            "      \"number\": \"Clause number (e.g. 1, 1.1, A)\",\n" +
            "      \"title\": \"Clause heading or label (e.g. Prices, Warranty, Payment Terms)\",\n" +
            "      \"text\": \"Complete verbatim text of this clause. Copy EVERY word exactly as written — " +
            "do NOT shorten, paraphrase, or omit any sentence. Do NOT remove vendor company names embedded in " +
            "legal text (e.g. warranty clauses referencing the vendor's factory) — copy them verbatim.\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"currency\": \"Currency code (e.g., USD, EUR, INR, IDR)\",\n" +
            "  \"subtotal\": 1000.00,\n" +
            "  \"discount\": 50.00,\n" +
            "  \"tax\": 100.00,\n" +
            "  \"totalAmount\": 1050.00,\n" +
            "  \"deliveryTime\": \"Delivery lead time — only if explicitly stated. Return null if not present.\",\n" +
            "  \"warranty\": \"Warranty terms — only if explicitly stated. Return null if not present.\",\n" +
            "  \"paymentTerms\": \"Payment terms — only if explicitly stated. Return null if not present.\",\n" +
            "  \"validity\": \"Validity date or period of the quote — only if explicitly stated. Return null if not present.\",\n" +
            "  \"notes\": \"Any extra notes, conditions or remarks explicitly present in the source.\",\n" +
            "  \"lineItems\": [\n" +
            "    {\n" +
            "      \"itemName\": \"Item name, service description, or part number\",\n" +
            "      \"description\": \"Detailed description of item or service\",\n" +
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
            "EXTRACTION RULES (apply to every field):\n" +
            "- Works for ANY quote type: machine, software/IT, professional service, logistics, maintenance contract, etc.\n" +
            "- Copy all text fields VERBATIM from the source document. Never paraphrase, summarize, reword, or omit content.\n" +
            "- Extract any spec key-value pairs (e.g. Model, SLA, Speed, Dimensions, Support Tier) into `specificationAttributes` array.\n" +
            "- If a quote has NO specification attributes, return `specificationAttributes`: null or []. Do NOT output empty/fake keys.\n" +
            "- Preserve exact wording, numbers, units, decimal formatting, and punctuation.\n" +
            "- Extract ALL line items and optional items — every single row from every pricing table.\n" +
            "- MUST EXTRACT ALL numbered or bulleted Terms & Conditions clauses into `termsAndConditionsList` array with their COMPLETE verbatim text (number, title, text). Never truncate a clause or return null if T&C clauses are present in the text.\n" +
            "- Do NOT extract signature blocks, sign-off text (e.g. 'Best regards', 'Yours Sincerely', " +
            "  signatory names/titles/closings) into any field — these are template elements, ignore them.\n" +
            "- Do NOT extract logos, headers, or footer branding.\n" +
            "- If a field is not present in the source, return null (not an empty string, not a default value).";
    }



    private String getSupplementarySystemPrompt() {
        return "You are an assistant that extracts supplementary quotation line items and optional items from document sections.\n" +
                "You must output a single JSON object matching this structure:\n" +
                "{\n" +
                "  \"lineItems\": [\n" +
                "    {\n" +
                "      \"itemName\": \"Item name\",\n" +
                "      \"description\": \"Item description\",\n" +
                "      \"quantity\": 1.0,\n" +
                "      \"unitPrice\": 100.00,\n" +
                "      \"totalPrice\": 100.00\n" +
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
                "Only extract actual line items or optional items present in this text. Copy all fields verbatim. If none are found, return empty arrays.";
    }
}
