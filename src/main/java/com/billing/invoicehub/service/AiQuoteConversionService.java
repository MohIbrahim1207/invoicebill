package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.AiQuotationEditDto;
import com.billing.invoicehub.dto.AiStatisticsDto;
import com.billing.invoicehub.entity.AiQuoteConversion;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AiQuoteConversionRepository;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class AiQuoteConversionService {

    private static final Logger logger = LoggerFactory.getLogger(AiQuoteConversionService.class);

    private final AiQuoteConversionRepository repository;
    private final PdfExtractionService pdfExtractionService;
    private final AIProvider aiProvider;
    private final FlowForceQuotePdfService flowForceQuotePdfService;
    private final CloudinaryService cloudinaryService;
    private final Gson gson;

    public AiQuoteConversionService(AiQuoteConversionRepository repository,
                                    PdfExtractionService pdfExtractionService,
                                    AIProvider aiProvider,
                                    FlowForceQuotePdfService flowForceQuotePdfService,
                                    CloudinaryService cloudinaryService) {
        this.repository = repository;
        this.pdfExtractionService = pdfExtractionService;
        this.aiProvider = aiProvider;
        this.flowForceQuotePdfService = flowForceQuotePdfService;
        this.cloudinaryService = cloudinaryService;
        this.gson = new Gson();
    }

    /**
     * Retrieves all conversions order by ID desc.
     */
    public List<AiQuoteConversion> getAllConversions() {
        return repository.findAllWithUserOrderByIdDesc();
    }

    /**
     * Retrieves a conversion with Eagerly fetched User.
     */
    public Optional<AiQuoteConversion> getConversionById(Long id) {
        return repository.findByIdWithUser(id);
    }

    /**
     * Uploads the quotation PDF, extracts text, calls AI provider, and persists the result.
     * Catches and records any errors inside the entity to keep track of failures in history.
     */
    @Transactional
    public AiQuoteConversion initiateConversion(MultipartFile file, AppUser user) {
        long startTime = System.currentTimeMillis();
        
        AiQuoteConversion conversion = new AiQuoteConversion();
        conversion.setOriginalFileName(file.getOriginalFilename());
        conversion.setUser(user);
        conversion.setProcessingDate(LocalDateTime.now());
        conversion.setStatus("PROCESSING");

        // 1. Upload original PDF to Cloudinary
        String fileUrl;
        try {
            if (cloudinaryService == null) {
                throw new IllegalStateException("Cloudinary file storage service is not configured.");
            }
            fileUrl = cloudinaryService.uploadSupportingDocument(file);
            conversion.setOriginalFileUrl(fileUrl);
        } catch (Exception e) {
            logger.error("Cloudinary upload failed for quotation PDF: {}", e.getMessage());
            conversion.setStatus("FAILED");
            conversion.setErrorMessage("Cloudinary Upload Error: " + e.getMessage());
            conversion.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            return repository.save(conversion);
        }

        // Save progress status to database
        conversion = repository.save(conversion);

        // 2. Perform text extraction and AI API request
        try {
            byte[] fileBytes = file.getBytes();
            
            // Extract text
            String extractedText = pdfExtractionService.extractText(fileBytes);
            if (extractedText == null || extractedText.isBlank()) {
                throw new IOException("No text could be extracted from this PDF document (scanned PDF without OCR support).");
            }

            // Extract structured JSON via AI Provider
            String structuredJson = aiProvider.extractQuotation(extractedText);
            
            // Deserialize, extract machine image if present, and save
            AiQuotationEditDto editDto = gson.fromJson(structuredJson, AiQuotationEditDto.class);
            byte[] productImageBytes = pdfExtractionService.extractProductImage(fileBytes);
            if (productImageBytes != null) {
                String base64Image = java.util.Base64.getEncoder().encodeToString(productImageBytes);
                editDto.setProductImageBase64(base64Image);
            }
            String updatedJson = gson.toJson(editDto);
            
            conversion.setExtractedJson(updatedJson);
            conversion.setStatus("PENDING_REVIEW");
            conversion.setVersionHistory("Version 1 initialized by AI parser at " + 
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            
        } catch (Exception e) {
            logger.error("AI parsing failed for quotation: {}", e.getMessage(), e);
            conversion.setStatus("FAILED");
            conversion.setErrorMessage(e.getMessage());
        }

        conversion.setProcessingTimeMs(System.currentTimeMillis() - startTime);
        return repository.save(conversion);
    }

    /**
     * Saves user updates as a draft.
     */
    @Transactional
    public AiQuoteConversion saveDraft(Long id, AiQuotationEditDto editDto, AppUser user) {
        AiQuoteConversion conversion = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Quotation conversion record not found."));

        // Restore productImageBase64 from existing draft if missing in the incoming DTO
        if (editDto.getProductImageBase64() == null || editDto.getProductImageBase64().isEmpty()) {
            if (conversion.getExtractedJson() != null && !conversion.getExtractedJson().isBlank()) {
                try {
                    AiQuotationEditDto existingDto = gson.fromJson(conversion.getExtractedJson(), AiQuotationEditDto.class);
                    if (existingDto.getProductImageBase64() != null) {
                        editDto.setProductImageBase64(existingDto.getProductImageBase64());
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse existing JSON to restore product image: {}", e.getMessage());
                }
            }
        }

        String updatedJson = gson.toJson(editDto);
        conversion.setExtractedJson(updatedJson);
        conversion.setVersion(conversion.getVersion() + 1);

        String auditLog = String.format("Version %d: Draft saved by %s at %s\n", 
                conversion.getVersion(), 
                user.getUsername(), 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        conversion.setVersionHistory((conversion.getVersionHistory() == null ? "" : conversion.getVersionHistory()) + auditLog);
        return repository.save(conversion);
    }

    /**
     * Approves the extraction, updates data, generates the Flow Force branded quotation PDF, 
     * uploads it to Cloudinary, and finalizes the record.
     */
    @Transactional
    public AiQuoteConversion approveAndGenerate(Long id, AiQuotationEditDto editDto, AppUser user) {
        AiQuoteConversion conversion = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Quotation conversion record not found."));

        // Restore productImageBase64 from existing draft if missing in the incoming DTO
        if (editDto.getProductImageBase64() == null || editDto.getProductImageBase64().isEmpty()) {
            if (conversion.getExtractedJson() != null && !conversion.getExtractedJson().isBlank()) {
                try {
                    AiQuotationEditDto existingDto = gson.fromJson(conversion.getExtractedJson(), AiQuotationEditDto.class);
                    if (existingDto.getProductImageBase64() != null) {
                        editDto.setProductImageBase64(existingDto.getProductImageBase64());
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse existing JSON to restore product image: {}", e.getMessage());
                }
            }
        }

        // 1. Update data draft first
        String updatedJson = gson.toJson(editDto);
        conversion.setExtractedJson(updatedJson);
        conversion.setVersion(conversion.getVersion() + 1);

        // 2. Generate Flow Force Quote PDF
        byte[] pdfBytes = flowForceQuotePdfService.generateFlowForceQuotePdf(editDto);
        String pdfFileName = "FlowForce_Quotation_" + (editDto.getQuotationNumber() != null ? editDto.getQuotationNumber() : id) + ".pdf";

        // 3. Upload generated PDF to Cloudinary
        try {
            if (cloudinaryService == null) {
                throw new IllegalStateException("Cloudinary file storage service is not configured.");
            }
            String generatedUrl = cloudinaryService.uploadQuotePdf(pdfBytes, pdfFileName);
            conversion.setGeneratedQuoteFileName(pdfFileName);
            conversion.setGeneratedQuoteFileUrl(generatedUrl);
            conversion.setStatus("GENERATED");
        } catch (Exception e) {
            logger.error("Failed to upload generated Flow Force quote to Cloudinary: {}", e.getMessage());
            throw new IllegalStateException("Failed to save generated quote file: " + e.getMessage(), e);
        }

        String auditLog = String.format("Version %d: Approved and quote generated by %s at %s\n", 
                conversion.getVersion(), 
                user.getUsername(), 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        conversion.setVersionHistory((conversion.getVersionHistory() == null ? "" : conversion.getVersionHistory()) + auditLog);
        return repository.save(conversion);
    }

    /**
     * Deletes a conversion record by ID.
     */
    @Transactional
    public void deleteConversion(Long id) {
        AiQuoteConversion conversion = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Quotation conversion record not found."));
        
        // Delete files from Cloudinary
        if (cloudinaryService != null) {
            if (conversion.getOriginalFileUrl() != null) {
                cloudinaryService.deleteFile(conversion.getOriginalFileUrl());
            }
            if (conversion.getGeneratedQuoteFileUrl() != null) {
                cloudinaryService.deleteFile(conversion.getGeneratedQuoteFileUrl());
            }
        }
        
        repository.deleteById(id);
    }

    /**
     * Gathers and calculates dashboard metrics.
     */
    public AiStatisticsDto getStatistics() {
        AiStatisticsDto stats = new AiStatisticsDto();
        long total = repository.count();
        long success = repository.countSuccessfulConversions();
        
        stats.setTotalConversions(total);
        stats.setPendingReviews(repository.countByStatus("PENDING_REVIEW"));
        stats.setQuotesGeneratedToday(repository.countQuotesGeneratedToday(LocalDate.now().atStartOfDay()));
        stats.setAverageProcessingTimeSec(repository.getAverageProcessingTimeMs() / 1000.0);
        
        double successRate = total > 0 ? (success * 100.0 / total) : 100.0;
        stats.setSuccessRate(Math.round(successRate * 10.0) / 10.0); // round to 1 decimal place

        return stats;
    }
}
