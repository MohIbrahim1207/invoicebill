package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.AiChatMessageDto;
import com.billing.invoicehub.dto.AiQuoteSessionDto;
import com.billing.invoicehub.dto.AiQuotationEditDto;
import com.billing.invoicehub.entity.AiQuoteMessage;
import com.billing.invoicehub.entity.AiQuoteSession;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AiQuoteSessionRepository;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiQuoteSessionService {

    private static final Logger logger = LoggerFactory.getLogger(AiQuoteSessionService.class);

    private final AiQuoteSessionRepository sessionRepository;
    private final AiQuoteChatService chatService;
    private final PdfExtractionService pdfExtractionService;
    private final CloudinaryService cloudinaryService;
    private final FlowForceQuotePdfService flowForceQuotePdfService;
    private final FlowForceQuoteDocxService flowForceQuoteDocxService;
    private final Gson gson;

    public AiQuoteSessionService(
            AiQuoteSessionRepository sessionRepository,
            AiQuoteChatService chatService,
            PdfExtractionService pdfExtractionService,
            CloudinaryService cloudinaryService,
            FlowForceQuotePdfService flowForceQuotePdfService,
            FlowForceQuoteDocxService flowForceQuoteDocxService) {
        this.sessionRepository = sessionRepository;
        this.chatService = chatService;
        this.pdfExtractionService = pdfExtractionService;
        this.cloudinaryService = cloudinaryService;
        this.flowForceQuotePdfService = flowForceQuotePdfService;
        this.flowForceQuoteDocxService = flowForceQuoteDocxService;
        this.gson = new Gson();
    }

    @Transactional
    public AiQuoteSessionDto createSession(AppUser user, String initialTitle) {
        AiQuoteSession session = new AiQuoteSession();
        session.setTitle(initialTitle != null && !initialTitle.isBlank() ? initialTitle : "New Quotation Assistant");
        session.setStatus("DRAFT");
        session.setCreatedBy(user);

        AiQuotationEditDto initialDto = new AiQuotationEditDto();
        session.setStructuredQuoteJson(gson.toJson(initialDto));

        AiQuoteMessage welcomeMsg = new AiQuoteMessage();
        welcomeMsg.setSession(session);
        welcomeMsg.setSender("ASSISTANT");
        welcomeMsg.setContent("Hello! I am your Flow Force AI Quotation Assistant. You can paste quotation text, upload a supplier PDF/image, or describe what you need to create a Flow Force quotation.");
        session.getMessages().add(welcomeMsg);

        AiQuoteSession saved = sessionRepository.save(session);
        return toDto(saved, true);
    }

    @Transactional(readOnly = true)
    public List<AiQuoteSessionDto> getUserSessions(AppUser user) {
        List<AiQuoteSession> sessions;
        if (user != null && user.getRoles().stream().anyMatch(r -> "ROLE_ADMIN".equals(r.getName()) || "ROLE_SUPER_ADMIN".equals(r.getName()))) {
            sessions = sessionRepository.findAllByOrderByUpdatedAtDesc();
        } else if (user != null) {
            sessions = sessionRepository.findByCreatedByIdOrderByUpdatedAtDesc(user.getId());
        } else {
            sessions = Collections.emptyList();
        }
        return sessions.stream().map(s -> toDto(s, false)).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<AiQuoteSessionDto> getSessionDto(Long sessionId, AppUser user) {
        return findSessionForUser(sessionId, user).map(s -> toDto(s, true));
    }

    @Transactional
    public AiChatMessageDto sendMessage(Long sessionId, String textContent, MultipartFile file, AppUser user) throws IOException, InterruptedException {
        AiQuoteSession session = findSessionForUser(sessionId, user)
                .orElseThrow(() -> new IllegalArgumentException("Quote session not found or access denied."));

        String attachedText = "";
        String attachmentUrl = null;
        String attachmentFilename = null;

        if (file != null && !file.isEmpty()) {
            attachmentFilename = file.getOriginalFilename();
            if (cloudinaryService != null) {
                try {
                    attachmentUrl = cloudinaryService.uploadSupportingDocument(file);
                } catch (Exception e) {
                    logger.warn("Could not upload chat attachment to Cloudinary: {}", e.getMessage());
                }
            }

            if (file.getOriginalFilename() != null && file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
                try {
                    attachedText = pdfExtractionService.extractText(file.getBytes());
                } catch (Exception e) {
                    logger.error("Failed to extract PDF text from chat attachment: {}", e.getMessage());
                }
            }
        }

        // Save User Message
        AiQuoteMessage userMsg = new AiQuoteMessage();
        userMsg.setSession(session);
        userMsg.setSender("USER");
        userMsg.setContent(textContent != null ? textContent.trim() : "");
        userMsg.setAttachmentUrl(attachmentUrl);
        userMsg.setAttachmentFilename(attachmentFilename);
        session.getMessages().add(userMsg);

        // Load existing structured quote state
        AiQuotationEditDto currentQuoteState;
        if (session.getStructuredQuoteJson() != null && !session.getStructuredQuoteJson().isBlank()) {
            currentQuoteState = gson.fromJson(session.getStructuredQuoteJson(), AiQuotationEditDto.class);
        } else {
            currentQuoteState = new AiQuotationEditDto();
        }

        // Build recent conversation history
        List<Map<String, String>> historyList = new ArrayList<>();
        for (AiQuoteMessage m : session.getMessages()) {
            Map<String, String> hm = new HashMap<>();
            hm.put("sender", m.getSender());
            hm.put("content", m.getContent() != null ? m.getContent() : "");
            historyList.add(hm);
        }

        // Process with AI Chat Service
        AiQuoteChatService.ChatAnalysisResult analysis = chatService.processChatMessage(
                textContent, attachedText, currentQuoteState, historyList);

        // Perform deterministic financial calculations in Java
        AiQuotationEditDto updatedQuote = analysis.updatedQuoteData;
        recalculateTotals(updatedQuote);

        // Backend Customer Completeness Validation
        processCustomerState(textContent, updatedQuote, analysis);

        // Update session title dynamically if supplier or product is detected
        if (updatedQuote.getSupplierName() != null && !updatedQuote.getSupplierName().isBlank()) {
            session.setTitle(updatedQuote.getSupplierName() + " Quote");
        } else if (updatedQuote.getMachineName() != null && !updatedQuote.getMachineName().isBlank()) {
            session.setTitle(updatedQuote.getMachineName());
        }

        // Update session status & state
        if (analysis.quoteComplete) {
            session.setStatus("READY");
        } else {
            session.setStatus("ANALYZING");
        }
        session.setStructuredQuoteJson(gson.toJson(updatedQuote));

        // Save Assistant Message
        AiQuoteMessage assistantMsg = new AiQuoteMessage();
        assistantMsg.setSession(session);
        assistantMsg.setSender("ASSISTANT");
        assistantMsg.setContent(analysis.replyMessage);
        session.getMessages().add(assistantMsg);

        sessionRepository.save(session);

        // Build Response DTO
        AiChatMessageDto responseDto = toMessageDto(assistantMsg);
        responseDto.setQuoteData(updatedQuote);
        responseDto.setMissingFields(analysis.missingFields);
        responseDto.setQuoteComplete(analysis.quoteComplete);
        return responseDto;
    }

    @Transactional
    public AiQuoteSessionDto updateStructuredQuote(Long sessionId, AiQuotationEditDto updatedQuote, AppUser user) {
        AiQuoteSession session = findSessionForUser(sessionId, user)
                .orElseThrow(() -> new IllegalArgumentException("Quote session not found or access denied."));

        recalculateTotals(updatedQuote);
        session.setStructuredQuoteJson(gson.toJson(updatedQuote));
        session.setStatus("READY");

        AiQuoteMessage sysMsg = new AiQuoteMessage();
        sysMsg.setSession(session);
        sysMsg.setSender("SYSTEM");
        sysMsg.setContent("Quotation details were manually updated by user.");
        session.getMessages().add(sysMsg);

        AiQuoteSession saved = sessionRepository.save(session);
        return toDto(saved, true);
    }

    @Transactional
    public AiQuoteSessionDto generateFlowForcePdf(Long sessionId, AppUser user) throws Exception {
        return generateFlowForceQuote(sessionId, com.billing.invoicehub.enums.OutputFormat.PDF, user);
    }

    @Transactional
    public AiQuoteSessionDto generateFlowForceQuote(Long sessionId, com.billing.invoicehub.enums.OutputFormat outputFormat, AppUser user) throws Exception {
        AiQuoteSession session = findSessionForUser(sessionId, user)
                .orElseThrow(() -> new IllegalArgumentException("Quote session not found or access denied."));

        if (session.getStructuredQuoteJson() == null || session.getStructuredQuoteJson().isBlank()) {
            throw new IllegalStateException("Structured quote data is missing.");
        }

        if (outputFormat == null) {
            outputFormat = com.billing.invoicehub.enums.OutputFormat.PDF;
        }

        AiQuotationEditDto editDto = gson.fromJson(session.getStructuredQuoteJson(), AiQuotationEditDto.class);
        recalculateTotals(editDto);

        String qNum = (editDto.getQuotationNumber() != null ? editDto.getQuotationNumber() : String.valueOf(System.currentTimeMillis()));
        String baseFilename = "FlowForce_Quotation_" + qNum;

        String pdfFilename = null;
        String pdfUrl = null;
        String docxFilename = null;
        String docxUrl = null;

        // Generate PDF if requested
        if (outputFormat == com.billing.invoicehub.enums.OutputFormat.PDF || outputFormat == com.billing.invoicehub.enums.OutputFormat.BOTH) {
            byte[] pdfBytes = flowForceQuotePdfService.generateQuotationPdf(editDto);
            pdfFilename = baseFilename + ".pdf";
            if (cloudinaryService != null) {
                try {
                    pdfUrl = cloudinaryService.uploadQuotePdf(pdfBytes, pdfFilename);
                } catch (Exception e) {
                    logger.warn("Could not upload generated PDF to Cloudinary: {}", e.getMessage());
                }
            }
            session.setGeneratedPdfFilename(pdfFilename);
            session.setGeneratedPdfUrl(pdfUrl);
        }

        // Generate DOCX if requested
        if (outputFormat == com.billing.invoicehub.enums.OutputFormat.DOCX || outputFormat == com.billing.invoicehub.enums.OutputFormat.BOTH) {
            byte[] docxBytes = flowForceQuoteDocxService.generateFlowForceQuoteDocx(editDto);
            docxFilename = baseFilename + ".docx";
            if (cloudinaryService != null) {
                try {
                    docxUrl = cloudinaryService.uploadQuoteDocx(docxBytes, docxFilename);
                } catch (Exception e) {
                    logger.warn("Could not upload generated DOCX to Cloudinary: {}", e.getMessage());
                }
            }
            session.setGeneratedDocxFilename(docxFilename);
            session.setGeneratedDocxUrl(docxUrl);
        }

        session.setStatus("GENERATED");

        AiQuoteMessage confirmMsg = new AiQuoteMessage();
        confirmMsg.setSession(session);
        confirmMsg.setSender("ASSISTANT");
        String formatLabel = outputFormat == com.billing.invoicehub.enums.OutputFormat.BOTH ? "PDF & Word (.docx)" : (outputFormat == com.billing.invoicehub.enums.OutputFormat.DOCX ? "Word (.docx)" : "PDF");
        confirmMsg.setContent("✓ Your Flow Force quotation " + formatLabel + " has been generated successfully! You can download your file(s) below.");
        confirmMsg.setAttachmentFilename(pdfFilename != null ? pdfFilename : docxFilename);
        confirmMsg.setAttachmentUrl(pdfUrl != null ? pdfUrl : docxUrl);
        session.getMessages().add(confirmMsg);

        AiQuoteSession saved = sessionRepository.save(session);
        return toDto(saved, true);
    }

    @Transactional
    public boolean deleteSession(Long sessionId, AppUser user) {
        Optional<AiQuoteSession> sessionOpt = findSessionForUser(sessionId, user);
        if (sessionOpt.isPresent()) {
            sessionRepository.delete(sessionOpt.get());
            return true;
        }
        return false;
    }

    private Optional<AiQuoteSession> findSessionForUser(Long sessionId, AppUser user) {
        Optional<AiQuoteSession> sessionOpt = sessionRepository.findByIdWithMessages(sessionId);
        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }
        AiQuoteSession session = sessionOpt.get();
        // Permission check
        if (user != null && user.getRoles().stream().anyMatch(r -> "ROLE_ADMIN".equals(r.getName()) || "ROLE_SUPER_ADMIN".equals(r.getName()))) {
            return Optional.of(session);
        }
        if (session.getCreatedBy() != null && user != null && session.getCreatedBy().getId().equals(user.getId())) {
            return Optional.of(session);
        }
        return Optional.empty();
    }

    private static final Set<String> UNSPECIFIED_TERMS = Set.of(
            "not specified", "nobody", "no customer", "n/a", "tbd", "unknown", "none"
    );

    private void processCustomerState(String userMessage, AiQuotationEditDto dto, AiQuoteChatService.ChatAnalysisResult analysis) {
        if (dto == null) return;

        String msgLower = userMessage != null ? userMessage.trim().toLowerCase() : "";
        String currentCust = dto.getCustomerName() != null ? dto.getCustomerName().trim() : "";
        String currentCustLower = currentCust.toLowerCase();

        boolean isUnspecifiedInput = UNSPECIFIED_TERMS.contains(msgLower) || UNSPECIFIED_TERMS.contains(currentCustLower);

        if (isUnspecifiedInput) {
            dto.setCustomerName("Not Specified");
            dto.setCustomerSpecified(false);
            if (analysis.missingFields != null) {
                analysis.missingFields.removeIf(f -> "customerName".equalsIgnoreCase(f) || "customer".equalsIgnoreCase(f));
            }
            if (analysis.replyMessage != null && analysis.replyMessage.toLowerCase().contains("customer name")) {
                analysis.replyMessage = "Understood. I'll mark the customer as Not Specified and continue with the quotation.";
            }
        } else if (!currentCust.isEmpty() && !"not specified".equalsIgnoreCase(currentCust)) {
            dto.setCustomerSpecified(true);
            if (analysis.missingFields != null) {
                analysis.missingFields.removeIf(f -> "customerName".equalsIgnoreCase(f) || "customer".equalsIgnoreCase(f));
            }
        }

        if (analysis.missingFields != null && analysis.missingFields.isEmpty()) {
            analysis.quoteComplete = true;
        }
    }

    private void recalculateTotals(AiQuotationEditDto dto) {
        if (dto == null) return;
        BigDecimal calculatedSubtotal = BigDecimal.ZERO;
        if (dto.getLineItems() != null) {
            for (AiQuotationEditDto.LineItem item : dto.getLineItems()) {
                if (item.getQuantity() != null && item.getUnitPrice() != null) {
                    BigDecimal lineTotal = item.getUnitPrice().multiply(item.getQuantity())
                            .setScale(2, RoundingMode.HALF_UP);
                    item.setTotalPrice(lineTotal);
                    calculatedSubtotal = calculatedSubtotal.add(lineTotal);
                } else if (item.getTotalPrice() != null) {
                    calculatedSubtotal = calculatedSubtotal.add(item.getTotalPrice());
                }
            }
        }
        if (calculatedSubtotal.compareTo(BigDecimal.ZERO) > 0) {
            dto.setSubtotal(calculatedSubtotal);
            BigDecimal disc = dto.getDiscount() != null ? dto.getDiscount() : BigDecimal.ZERO;
            BigDecimal taxVal = dto.getTax() != null ? dto.getTax() : BigDecimal.ZERO;
            dto.setTotalAmount(calculatedSubtotal.subtract(disc).add(taxVal).setScale(2, RoundingMode.HALF_UP));
        }
    }

    private AiQuoteSessionDto toDto(AiQuoteSession session, boolean includeMessages) {
        AiQuoteSessionDto dto = new AiQuoteSessionDto();
        dto.setId(session.getId());
        dto.setTitle(session.getTitle());
        dto.setStatus(session.getStatus());
        dto.setGeneratedPdfUrl(session.getGeneratedPdfUrl());
        dto.setGeneratedPdfFilename(session.getGeneratedPdfFilename());
        dto.setGeneratedDocxUrl(session.getGeneratedDocxUrl());
        dto.setGeneratedDocxFilename(session.getGeneratedDocxFilename());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
        if (session.getCreatedAt() != null) dto.setCreatedAt(session.getCreatedAt().format(formatter));
        if (session.getUpdatedAt() != null) dto.setUpdatedAt(session.getUpdatedAt().format(formatter));

        if (session.getStructuredQuoteJson() != null && !session.getStructuredQuoteJson().isBlank()) {
            try {
                dto.setStructuredQuoteData(gson.fromJson(session.getStructuredQuoteJson(), AiQuotationEditDto.class));
            } catch (Exception e) {
                logger.warn("Failed to parse structuredQuoteJson for session {}", session.getId());
            }
        }

        if (includeMessages && session.getMessages() != null) {
            List<AiChatMessageDto> msgDtos = session.getMessages().stream()
                    .map(this::toMessageDto)
                    .collect(Collectors.toList());
            dto.setMessages(msgDtos);
        }

        return dto;
    }

    private AiChatMessageDto toMessageDto(AiQuoteMessage msg) {
        AiChatMessageDto dto = new AiChatMessageDto();
        dto.setId(msg.getId());
        dto.setSender(msg.getSender());
        dto.setContent(msg.getContent());
        dto.setAttachmentUrl(msg.getAttachmentUrl());
        dto.setAttachmentFilename(msg.getAttachmentFilename());
        if (msg.getCreatedAt() != null) {
            dto.setCreatedAt(msg.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm")));
        }
        return dto;
    }
}
