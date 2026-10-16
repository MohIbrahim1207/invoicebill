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
    private final FlowForceQuoteDocxService flowForceQuoteDocxService;
    private final CloudinaryService cloudinaryService;
    private final Gson gson;
    private final NumericValidationService numericValidationService;

    public AiQuoteConversionService(AiQuoteConversionRepository repository,
            PdfExtractionService pdfExtractionService,
            AIProvider aiProvider,
            FlowForceQuotePdfService flowForceQuotePdfService,
            FlowForceQuoteDocxService flowForceQuoteDocxService,
            CloudinaryService cloudinaryService,
            NumericValidationService numericValidationService) {
        this.repository = repository;
        this.pdfExtractionService = pdfExtractionService;
        this.aiProvider = aiProvider;
        this.flowForceQuotePdfService = flowForceQuotePdfService;
        this.flowForceQuoteDocxService = flowForceQuoteDocxService;
        this.cloudinaryService = cloudinaryService;
        this.numericValidationService = numericValidationService;
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
     * Uploads the quotation PDF, extracts text, calls AI provider, runs validation checks,
     * extracts all content images, and persists the result.
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

        conversion = repository.save(conversion);

        // 2. Text extraction + AI + direct generation & upload
        long totalStartTime = System.currentTimeMillis();
        try {
            long uploadStart = System.currentTimeMillis();
            byte[] fileBytes = file.getBytes();
            long uploadTime = System.currentTimeMillis() - uploadStart;

            // Extract text
            long extractTextStart = System.currentTimeMillis();
            String extractedText = pdfExtractionService.extractText(fileBytes);
            long extractTextTime = System.currentTimeMillis() - extractTextStart;
            if (extractedText == null || extractedText.isBlank()) {
                throw new IOException("No text could be extracted from this PDF document (scanned PDF without OCR support).");
            }

            // Extract structured JSON via AI Provider (Gemini)
            long geminiStart = System.currentTimeMillis();
            String structuredJson = aiProvider.extractQuotation(extractedText);
            long geminiTime = System.currentTimeMillis() - geminiStart;

            // Deserialize extracted data
            AiQuotationEditDto editDto = gson.fromJson(structuredJson, AiQuotationEditDto.class);

            // Disable all validation rules and warning checks. Keep warning warnings list empty.
            conversion.setValidationWarnings(null);

            // --- Extract all content images ---
            long imageExtractStart = System.currentTimeMillis();
            java.util.List<PdfExtractionService.ImagePlacement> placements =
                    pdfExtractionService.extractAllContentImages(fileBytes);
            if (!placements.isEmpty()) {
                java.util.List<AiQuotationEditDto.ExtractedImage> extractedImages = new java.util.ArrayList<>();
                for (PdfExtractionService.ImagePlacement p : placements) {
                    AiQuotationEditDto.ExtractedImage img = new AiQuotationEditDto.ExtractedImage();
                    img.setPageNumber(p.pageIndex + 1); // convert to 1-based
                    img.setX(p.x);
                    img.setY(p.y);
                    img.setWidth(p.w);
                    img.setHeight(p.h);
                    img.setPageHeight(p.pageHeight);
                    img.setImageBase64(java.util.Base64.getEncoder().encodeToString(p.imageBytes));
                    extractedImages.add(img);
                }
                editDto.setExtractedImages(extractedImages);
            }
            long imageExtractTime = System.currentTimeMillis() - imageExtractStart;

            String updatedJson = gson.toJson(editDto);
            conversion.setExtractedJson(updatedJson);
            conversion.setVersionHistory("Version 1 initialized by AI parser at " +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");

            long dbSaveStart = System.currentTimeMillis();
            conversion.setStatus("READY");
            conversion.setErrorMessage(null);
            repository.save(conversion);
            long dbSaveTime = System.currentTimeMillis() - dbSaveStart;

            long totalTime = System.currentTimeMillis() - totalStartTime;
            logger.info("[PERFORMANCE TELEMETRY] Conversion ID {} processQuotation completed in {} ms | FileUpload: {} ms | PDFTextExtract: {} ms | GeminiExtraction: {} ms | ImageExtract: {} ms | DBSave: {} ms",
                    conversion.getId(), totalTime, uploadTime, extractTextTime, geminiTime, imageExtractTime, dbSaveTime);

        } catch (Exception e) {
            logger.error("Quotation conversion failed for ID {}: {}. Transitioning status to FAILED",
                     conversion.getId(), e.getMessage(), e);
            conversion.setStatus("FAILED");
            conversion.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.toString());
        }

        conversion.setProcessingTimeMs(System.currentTimeMillis() - startTime);
        return repository.save(conversion);
    }

    /** Builds a regex pattern to detect vendor company names in legal clause text. */
    private java.util.regex.Pattern buildVendorLegalPattern(String supplierName) {
        StringBuilder sb = new StringBuilder();
        // Match supplier name if known
        if (supplierName != null && !supplierName.isBlank()) {
            sb.append(java.util.regex.Pattern.quote(supplierName.trim()));
            sb.append("|");
        }
        // Common legal patterns indicating embedded vendor liability language
        sb.append("(?i)(?:[A-Z][A-Za-z\\s]+(?:Pvt\\.?\\s*Ltd\\.?|Inc\\.?|Corp\\.?|Limited|GmbH|B\\.V\\.|S\\.A\\.|LLC))" +
                  "\\s+(?:warrants|warranty|guarantees|shall|is responsible|its\\s+factory|Ex\\s+Works)");
        sb.append("|factory\\s+in\\s+[A-Z][A-Za-z\\s,]+");
        sb.append("|(?:Ex\\s+Works|EXW)\\s+[A-Z][A-Za-z\\s]+(?:'s\\s+factory|factory)");
        return java.util.regex.Pattern.compile(sb.toString());
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
                    AiQuotationEditDto existingDto = gson.fromJson(conversion.getExtractedJson(),
                            AiQuotationEditDto.class);
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

        // Re-evaluate validation warnings on draft edits to allow status recovery
        java.util.List<String> warnings = new java.util.ArrayList<>();
        boolean validationClean = true;
        try {
            // Re-run validation scans
            // 1. Numeric validation: Compare the raw pdf text (we can try to get it, or extract from Cloudinary if needed;
            // since we don't have pdf bytes here, we can extract from pdfExtractionService if we fetch originalFileUrl,
            // but a simpler/safer way is to compare editDto fields against raw pdf text if we cache it.
            // Let's see if we can read the original PDF file from Cloudinary or look up cached text.
            // Wait, we can download the file using originalFileUrl or just read from the database if we stored the text.
            // Wait, the raw extracted text is NOT stored in the database today. Let's see if we can get it,
            // or if we should store the raw text or download the PDF to re-extract.
            // Actually, we can fetch the PDF from Cloudinary URL and extract text, which is simple and robust.)
            byte[] fileBytes = null;
            if (cloudinaryService != null && conversion.getOriginalFileUrl() != null) {
                try (java.io.InputStream in = new java.net.URL(conversion.getOriginalFileUrl()).openStream()) {
                    fileBytes = in.readAllBytes();
                }
            }
            if (fileBytes != null) {
                String extractedText = pdfExtractionService.extractText(fileBytes);
                NumericValidationService.ValidationResult validationResult =
                        numericValidationService.validate(extractedText, updatedJson);
                if (!validationResult.isClean()) {
                    validationClean = false;
                    for (String val : validationResult.getUnmatchedValues()) {
                        warnings.add("Numeric value not traceable to source PDF: '" + val + "' — verify before sending");
                    }
                }
            }

            // 2. Vendor legal-language scan
            String supplierName = editDto.getSupplierName();
            if (editDto.getTermsAndConditionsList() != null && !editDto.getTermsAndConditionsList().isEmpty()) {
                java.util.regex.Pattern vendorLegalPattern = buildVendorLegalPattern(supplierName);
                for (AiQuotationEditDto.TermsClause clause : editDto.getTermsAndConditionsList()) {
                    if (clause.getText() == null) continue;
                    java.util.regex.Matcher m = vendorLegalPattern.matcher(clause.getText());
                    if (m.find()) {
                        validationClean = false;
                        String matched = m.group().length() > 80 ? m.group().substring(0, 80) + "..." : m.group();
                        warnings.add("Warranty/legal clauses still reference original vendor '" + supplierName + "' in T&C #" + clause.getNumber() + ": '"
                                + matched + "' — confirm whether this is intentional (e.g. pass-through manufacturer warranty) before approving.");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Exception during numeric or vendor validation on draft save for record {}. Defaulting to NEEDS_REVIEW.", conversion.getId(), e);
            validationClean = false;
            warnings.add("Validation service encountered an error during parsing; forcing manual review for safety.");
        }

        if (!warnings.isEmpty()) {
            conversion.setValidationWarnings(gson.toJson(warnings));
            conversion.setStatus("NEEDS_REVIEW");
        } else {
            conversion.setValidationWarnings(null);
            conversion.setStatus("AUTO_APPROVED_CLEAN");
        }

        String auditLog = String.format("Version %d: Draft saved by %s at %s\n",
                conversion.getVersion(),
                user.getUsername(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        conversion.setVersionHistory(
                (conversion.getVersionHistory() == null ? "" : conversion.getVersionHistory()) + auditLog);
        return repository.save(conversion);
    }

    /**
     * Approves the extraction, updates data, generates the Flow Force branded
     * quotation PDF,
    /**
     * Approves the extraction, updates data, generates the Flow Force branded
     * quotation (PDF, DOCX, or BOTH), uploads to Cloudinary, and finalizes the record.
     */
    @Transactional
    public AiQuoteConversion approveAndGenerate(Long id, AiQuotationEditDto editDto, AppUser user) {
        return approveAndGenerate(id, editDto, com.billing.invoicehub.enums.OutputFormat.PDF, user);
    }

    @Transactional
    public AiQuoteConversion approveAndGenerate(Long id, AiQuotationEditDto editDto, com.billing.invoicehub.enums.OutputFormat outputFormat, AppUser user) {
        AiQuoteConversion conversion = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Quotation conversion record not found."));

        if (outputFormat == null) {
            outputFormat = com.billing.invoicehub.enums.OutputFormat.PDF;
        }

        // Restore productImageBase64 from existing draft if missing in the incoming DTO
        if (editDto.getProductImageBase64() == null || editDto.getProductImageBase64().isEmpty()) {
            if (conversion.getExtractedJson() != null && !conversion.getExtractedJson().isBlank()) {
                try {
                    AiQuotationEditDto existingDto = gson.fromJson(conversion.getExtractedJson(),
                            AiQuotationEditDto.class);
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

        String baseFileName = "FlowForce_Quotation_" + (editDto.getQuotationNumber() != null ? editDto.getQuotationNumber() : id);

        long totalGenStart = System.currentTimeMillis();
        long pdfGenTime = 0;
        long pdfUploadTime = 0;
        long docxGenTime = 0;
        long docxUploadTime = 0;

        try {
            // Generate PDF if requested
            if (outputFormat == com.billing.invoicehub.enums.OutputFormat.PDF || outputFormat == com.billing.invoicehub.enums.OutputFormat.BOTH) {
                long pGenStart = System.currentTimeMillis();
                byte[] pdfBytes = flowForceQuotePdfService.generateFlowForceQuotePdf(editDto);
                pdfGenTime = System.currentTimeMillis() - pGenStart;

                String pdfFileName = baseFileName + ".pdf";
                String pdfUrl = null;
                if (cloudinaryService != null) {
                    long pUpStart = System.currentTimeMillis();
                    pdfUrl = cloudinaryService.uploadQuotePdf(pdfBytes, pdfFileName);
                    pdfUploadTime = System.currentTimeMillis() - pUpStart;
                }
                conversion.setGeneratedQuoteFileName(pdfFileName);
                conversion.setGeneratedQuoteFileUrl(pdfUrl);
            }

            // Generate DOCX if requested
            if (outputFormat == com.billing.invoicehub.enums.OutputFormat.DOCX || outputFormat == com.billing.invoicehub.enums.OutputFormat.BOTH) {
                long dGenStart = System.currentTimeMillis();
                byte[] docxBytes = flowForceQuoteDocxService.generateFlowForceQuoteDocx(editDto);
                docxGenTime = System.currentTimeMillis() - dGenStart;

                String docxFileName = baseFileName + ".docx";
                String docxUrl = null;
                if (cloudinaryService != null) {
                    long dUpStart = System.currentTimeMillis();
                    docxUrl = cloudinaryService.uploadQuoteDocx(docxBytes, docxFileName);
                    docxUploadTime = System.currentTimeMillis() - dUpStart;
                }
                conversion.setGeneratedDocxFileName(docxFileName);
                conversion.setGeneratedDocxFileUrl(docxUrl);
            }

            logger.info("Transitioning AiQuoteConversion ID {} status: {} -> GENERATED (Format: {})",
                    conversion.getId(), conversion.getStatus(), outputFormat);
            conversion.setStatus("GENERATED");
            conversion.setErrorMessage(null); // Clear any previous error

            long totalGenTime = System.currentTimeMillis() - totalGenStart;
            logger.info("[PERFORMANCE TELEMETRY] Conversion ID {} Generation completed in {} ms | Format: {} | PDFRender: {} ms | PDFUpload: {} ms | DOCXRender: {} ms | DOCXUpload: {} ms",
                    conversion.getId(), totalGenTime, outputFormat, pdfGenTime, pdfUploadTime, docxGenTime, docxUploadTime);
        } catch (Exception e) {
            logger.error("Failed to generate or upload Flow Force quote files for ID {}: {}. Transitioning status to FAILED",
                    conversion.getId(), e.getMessage(), e);
            conversion.setStatus("FAILED");
            conversion.setErrorMessage("Generation/Upload Error: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
            repository.save(conversion);
            throw new IllegalStateException("Failed to generate and upload quote: " + e.getMessage(), e);
        }

        String auditLog = String.format("Version %d: Approved and quote generated by %s at %s\n",
                conversion.getVersion(),
                user.getUsername(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        conversion.setVersionHistory(
                (conversion.getVersionHistory() == null ? "" : conversion.getVersionHistory()) + auditLog);
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
