package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.AiQuotationEditDto;
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
import java.util.*;

@Service
public class AiQuoteChatService {

    private static final Logger logger = LoggerFactory.getLogger(AiQuoteChatService.class);

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-3-flash}")
    private String modelName;

    private final HttpClient httpClient;
    private final Gson gson;

    public AiQuoteChatService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.gson = new Gson();
    }

    public static class ChatAnalysisResult {
        public String replyMessage;
        public AiQuotationEditDto updatedQuoteData;
        public List<String> missingFields = new ArrayList<>();
        public boolean quoteComplete = false;
    }

    public ChatAnalysisResult processChatMessage(
            String userMessage,
            String attachedText,
            AiQuotationEditDto currentQuoteState,
            List<Map<String, String>> historyMessages) throws IOException, InterruptedException {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured.");
        }

        String prompt = buildPrompt(userMessage, attachedText, currentQuoteState, historyMessages);
        String systemInstruction = getChatSystemPrompt();

        String rawAiResponse = queryGeminiRaw(prompt, systemInstruction);
        return parseAiResponse(rawAiResponse, currentQuoteState);
    }

    private String buildPrompt(
            String userMessage,
            String attachedText,
            AiQuotationEditDto currentQuoteState,
            List<Map<String, String>> historyMessages) {

        StringBuilder sb = new StringBuilder();
        sb.append("CURRENT STRUCTURED QUOTATION STATE (JSON):\n");
        sb.append(gson.toJson(currentQuoteState != null ? currentQuoteState : new AiQuotationEditDto())).append("\n\n");

        if (historyMessages != null && !historyMessages.isEmpty()) {
            sb.append("RECENT CONVERSATION HISTORY:\n");
            for (Map<String, String> msg : historyMessages) {
                sb.append(msg.get("sender")).append(": ").append(msg.get("content")).append("\n");
            }
            sb.append("\n");
        }

        if (attachedText != null && !attachedText.isBlank()) {
            sb.append("ATTACHED DOCUMENT EXTRACTED TEXT:\n");
            sb.append(attachedText).append("\n\n");
        }

        sb.append("NEW USER MESSAGE:\n").append(userMessage != null ? userMessage : "").append("\n");

        return sb.toString();
    }

    private String getChatSystemPrompt() {
        return """
                You are Flow Force's expert Conversational AI Quotation Assistant.
                Your task is to converse naturally with the user, extract quotation details, detect missing information, update structured quotation fields, and guide the user toward creating a Flow Force quotation.

                You must ALWAYS return a SINGLE JSON object with the exact structure:
                {
                  "replyMessage": "Natural language response to the user summarizing extracted items and asking for any missing details...",
                  "extractedQuoteData": {
                    "supplierName": "...",
                    "customerName": "...",
                    "customerAddress": "...",
                    "quotationNumber": "...",
                    "date": "...",
                    "machineName": "...",
                    "currency": "USD/EUR/IDR/SGD",
                    "subtotal": 0.00,
                    "totalAmount": 0.00,
                    "deliveryTime": "...",
                    "paymentTerms": "...",
                    "validity": "...",
                    "notes": "...",
                    "lineItems": [
                      {
                        "itemNo": 1,
                        "description": "...",
                        "quantity": 1,
                        "unitPrice": 100.00,
                        "totalPrice": 100.00
                      }
                    ]
                  },
                  "missingFields": ["customerName", "deliveryTime"],
                  "quoteComplete": false
                }

                Rules:
                1. Merge any new details from user message or attached document into `extractedQuoteData`. Do NOT erase existing valid fields unless corrected by user.
                2. CUSTOMER NAME HANDLING:
                   - If the user explicitly indicates that customer is not specified or provides terms like "Not specified", "nobody", "no customer", "N/A", "TBD", "unknown", "none":
                     Set `customerName` to "Not Specified", set `customerSpecified` to false, do NOT include "customerName" in `missingFields`, and do NOT ask for customer name again.
                   - If the user later provides an actual customer name (e.g. "ABC Industries"):
                     Update `customerName` to that real customer name, and set `customerSpecified` to true.
                   - If the customer was NEVER mentioned by the user or document at all:
                     Leave `customerName` null or empty, and include "customerName" in `missingFields`.
                3. Evaluate required fields for a complete Flow Force quotation: `customerName` (or `customerSpecified` == false), `product` or `machineName`, line items with quantities and unit prices.
                4. If essential details are missing, list them in `missingFields`, set `quoteComplete` to false, and ask for them politely in `replyMessage`.
                5. When all key quotation details are available, set `quoteComplete` to true and instruct the user to review and click 'Generate Flow Force Quote'.
                """;
    }

    private String queryGeminiRaw(String promptText, String systemInstruction) throws IOException, InterruptedException {
        Map<String, Object> requestMap = new HashMap<>();
        List<Map<String, Object>> contentsList = new ArrayList<>();
        Map<String, Object> contentMap = new HashMap<>();
        List<Map<String, String>> partsList = new ArrayList<>();
        Map<String, String> partMap = new HashMap<>();
        partMap.put("text", promptText);
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
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("temperature", 0.2);
        requestMap.put("generationConfig", generationConfig);

        String requestBody = gson.toJson(requestMap);
        String actualModel = (modelName == null || modelName.isBlank()) ? "gemini-3-flash" : modelName;
        String endpoint = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                actualModel,
                apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(90))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
            return responseJson.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();
        } else {
            throw new IOException("Gemini API call failed with HTTP status: " + response.statusCode() + " Body: " + response.body());
        }
    }

    private ChatAnalysisResult parseAiResponse(String rawAiResponse, AiQuotationEditDto currentState) {
        ChatAnalysisResult result = new ChatAnalysisResult();
        try {
            JsonObject json = JsonParser.parseString(rawAiResponse).getAsJsonObject();
            if (json.has("replyMessage") && !json.get("replyMessage").isJsonNull()) {
                result.replyMessage = json.get("replyMessage").getAsString();
            } else {
                result.replyMessage = "I have processed the quotation details.";
            }

            if (json.has("extractedQuoteData") && json.get("extractedQuoteData").isJsonObject()) {
                result.updatedQuoteData = gson.fromJson(json.get("extractedQuoteData"), AiQuotationEditDto.class);
            } else {
                result.updatedQuoteData = currentState != null ? currentState : new AiQuotationEditDto();
            }

            if (json.has("missingFields") && json.get("missingFields").isJsonArray()) {
                json.getAsJsonArray("missingFields").forEach(elem -> result.missingFields.add(elem.getAsString()));
            }

            if (json.has("quoteComplete") && !json.get("quoteComplete").isJsonNull()) {
                result.quoteComplete = json.get("quoteComplete").getAsBoolean();
            }
        } catch (Exception e) {
            logger.warn("Failed to parse Gemini structured JSON: {}", e.getMessage());
            result.replyMessage = rawAiResponse;
            result.updatedQuoteData = currentState != null ? currentState : new AiQuotationEditDto();
        }
        return result;
    }
}
