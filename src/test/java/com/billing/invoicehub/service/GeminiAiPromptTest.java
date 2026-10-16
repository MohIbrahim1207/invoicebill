package com.billing.invoicehub.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class GeminiAiPromptTest {

    private GeminiAiService geminiAiService;

    @BeforeEach
    public void setUp() {
        geminiAiService = new GeminiAiService();
        ReflectionTestUtils.setField(geminiAiService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(geminiAiService, "modelName", "gemini-3-flash");
    }

    @Test
    @DisplayName("Anti-hallucination constraint text is present in the full system prompt sent to Gemini")
    public void testAntiHallucinationConstraintInSystemPrompt() throws Exception {
        Method method = GeminiAiService.class.getDeclaredMethod("getFullSystemPrompt");
        method.setAccessible(true);

        String systemPrompt = (String) method.invoke(geminiAiService);

        assertNotNull(systemPrompt, "System prompt should not be null");
        assertTrue(systemPrompt.contains("CRITICAL RULE — Non-Inference Constraint"),
                "Prompt must contain the non-inference critical rule header");
        assertTrue(systemPrompt.contains("Only extract information that is explicitly present in the source document"),
                "Prompt must instruct Gemini to only extract explicitly present information");
        assertTrue(systemPrompt.contains("NEVER fill in a plausible default, industry-standard value, or example"),
                "Prompt must explicitly forbid hallucinated defaults or examples");
    }

    @Test
    @DisplayName("Verbatim clause extraction rules are present in prompt for T&C and cover letter")
    public void testVerbatimExtractionRulesInPrompt() throws Exception {
        Method method = GeminiAiService.class.getDeclaredMethod("getFullSystemPrompt");
        method.setAccessible(true);

        String systemPrompt = (String) method.invoke(geminiAiService);

        assertTrue(systemPrompt.contains("Copy EVERY word exactly as written"),
                "Prompt must contain verbatim copy instructions for T&C clauses");
        assertTrue(systemPrompt.contains("Do NOT remove vendor company names embedded in legal text"),
                "Prompt must instruct Gemini to preserve embedded vendor company names in legal clauses");
    }
}
