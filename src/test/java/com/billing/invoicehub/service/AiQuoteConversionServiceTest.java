package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.AiQuotationEditDto;
import com.billing.invoicehub.entity.AiQuoteConversion;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AiQuoteConversionRepository;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AiQuoteConversionServiceTest {

    @Mock
    private AiQuoteConversionRepository repository;

    @Mock
    private PdfExtractionService pdfExtractionService;

    @Mock
    private AIProvider aiProvider;

    @Mock
    private FlowForceQuotePdfService flowForceQuotePdfService;

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private NumericValidationService numericValidationService;

    @InjectMocks
    private AiQuoteConversionService aiQuoteConversionService;

    private AppUser mockUser;
    private Gson gson;

    @BeforeEach
    public void setUp() {
        gson = new Gson();
        mockUser = new AppUser();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");
    }

    @Test
    @DisplayName("Vendor Legal Scanner: flags T&C containing original vendor name (e.g. IDEX India Pvt. Ltd.)")
    public void testVendorLegalScanner_FlagsOriginalVendorName() {
        Long recordId = 1L;
        AiQuoteConversion conversion = new AiQuoteConversion();
        conversion.setId(recordId);
        conversion.setOriginalFileUrl("https://cloudinary.com/dummy.pdf");
        conversion.setExtractedJson("{}");
        conversion.setVersion(1);

        when(repository.findById(recordId)).thenReturn(Optional.of(conversion));
        when(repository.save(any(AiQuoteConversion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiQuotationEditDto editDto = new AiQuotationEditDto();
        editDto.setSupplierName("IDEX India Pvt. Ltd.");

        List<AiQuotationEditDto.TermsClause> terms = new ArrayList<>();
        AiQuotationEditDto.TermsClause clause1 = new AiQuotationEditDto.TermsClause();
        clause1.setNumber("1");
        clause1.setTitle("Warranty");
        clause1.setText("IDEX India Pvt. Ltd. warrants that products supplied will be free from defects for 12 months.");
        terms.add(clause1);
        editDto.setTermsAndConditionsList(terms);

        AiQuoteConversion updated = aiQuoteConversionService.saveDraft(recordId, editDto, mockUser);

        assertEquals("NEEDS_REVIEW", updated.getStatus(), "Status should be NEEDS_REVIEW when vendor legal pattern matches");
        assertNotNull(updated.getValidationWarnings(), "Validation warnings should be populated");
        assertFalse(updated.getValidationWarnings().isEmpty(), "Validation warnings JSON string should not be empty");
    }

    @Test
    @DisplayName("Vendor Legal Scanner: clean T&C with no vendor reference passes cleanly")
    public void testVendorLegalScanner_CleanTcPasses() {
        Long recordId = 1L;
        AiQuoteConversion conversion = new AiQuoteConversion();
        conversion.setId(recordId);
        conversion.setOriginalFileUrl(null); // skip PDF download during mock
        conversion.setExtractedJson("{}");
        conversion.setVersion(1);

        when(repository.findById(recordId)).thenReturn(Optional.of(conversion));
        when(repository.save(any(AiQuoteConversion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiQuotationEditDto editDto = new AiQuotationEditDto();
        editDto.setSupplierName("FlowForce Pumps");

        List<AiQuotationEditDto.TermsClause> terms = new ArrayList<>();
        AiQuotationEditDto.TermsClause clause1 = new AiQuotationEditDto.TermsClause();
        clause1.setNumber("1");
        clause1.setTitle("Payment Terms");
        clause1.setText("100% advance payment along with official purchase order.");
        terms.add(clause1);
        editDto.setTermsAndConditionsList(terms);

        AiQuoteConversion updated = aiQuoteConversionService.saveDraft(recordId, editDto, mockUser);

        assertEquals("AUTO_APPROVED_CLEAN", updated.getStatus(), "Status should transition to AUTO_APPROVED_CLEAN when clean");
        assertNull(updated.getValidationWarnings(), "Validation warnings should be null when clean");
    }

    @Test
    @DisplayName("Vendor Legal Scanner: handles case-insensitivity and generic legal warranty pattern")
    public void testVendorLegalScanner_GenericLegalPattern() {
        Long recordId = 1L;
        AiQuoteConversion conversion = new AiQuoteConversion();
        conversion.setId(recordId);
        conversion.setOriginalFileUrl(null);
        conversion.setExtractedJson("{}");
        conversion.setVersion(1);

        when(repository.findById(recordId)).thenReturn(Optional.of(conversion));
        when(repository.save(any(AiQuoteConversion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiQuotationEditDto editDto = new AiQuotationEditDto();
        editDto.setSupplierName("Acme Corp");

        List<AiQuotationEditDto.TermsClause> terms = new ArrayList<>();
        AiQuotationEditDto.TermsClause clause1 = new AiQuotationEditDto.TermsClause();
        clause1.setNumber("2");
        clause1.setTitle("Delivery");
        clause1.setText("Delivery shall be Ex Works Acme Corp's factory in Mumbai.");
        terms.add(clause1);
        editDto.setTermsAndConditionsList(terms);

        AiQuoteConversion updated = aiQuoteConversionService.saveDraft(recordId, editDto, mockUser);

        assertEquals("NEEDS_REVIEW", updated.getStatus(), "Ex Works factory clause referencing supplier should trigger NEEDS_REVIEW");
    }

    @Test
    @DisplayName("Fail-safe behavior: Exception during draft validation forces NEEDS_REVIEW status")
    public void testValidationException_FailSafeToNeedsReview() {
        Long recordId = 1L;
        AiQuoteConversion conversion = new AiQuoteConversion();
        conversion.setId(recordId);
        // Provide invalid URL to trigger network/parsing exception in saveDraft validation
        conversion.setOriginalFileUrl("http://invalid-host-name-for-testing-12345.com/nonexistent.pdf");
        conversion.setExtractedJson("{}");
        conversion.setVersion(1);

        when(repository.findById(recordId)).thenReturn(Optional.of(conversion));
        when(repository.save(any(AiQuoteConversion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiQuotationEditDto editDto = new AiQuotationEditDto();
        editDto.setSupplierName("Any Vendor");

        AiQuoteConversion updated = aiQuoteConversionService.saveDraft(recordId, editDto, mockUser);

        assertEquals("NEEDS_REVIEW", updated.getStatus(), "Fail-safe: exception must default status to NEEDS_REVIEW");
        assertNotNull(updated.getValidationWarnings());
        assertTrue(updated.getValidationWarnings().contains("forcing manual review for safety"));
    }

    @Test
    @DisplayName("Draft editing: resolves flags and transitions NEEDS_REVIEW to AUTO_APPROVED_CLEAN when edited cleanly")
    public void testSaveDraft_ResolvesFlagsToClean() {
        Long recordId = 1L;
        AiQuoteConversion conversion = new AiQuoteConversion();
        conversion.setId(recordId);
        conversion.setStatus("NEEDS_REVIEW");
        conversion.setValidationWarnings("[\"Old warning\"]");
        conversion.setOriginalFileUrl(null);
        conversion.setExtractedJson("{}");
        conversion.setVersion(1);

        when(repository.findById(recordId)).thenReturn(Optional.of(conversion));
        when(repository.save(any(AiQuoteConversion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiQuotationEditDto cleanDto = new AiQuotationEditDto();
        cleanDto.setSupplierName("FlowForce Engineering");
        List<AiQuotationEditDto.TermsClause> cleanTerms = new ArrayList<>();
        AiQuotationEditDto.TermsClause clause = new AiQuotationEditDto.TermsClause();
        clause.setNumber("1");
        clause.setTitle("Warranty");
        clause.setText("Standard 12-month operational warranty.");
        cleanTerms.add(clause);
        cleanDto.setTermsAndConditionsList(cleanTerms);

        AiQuoteConversion updated = aiQuoteConversionService.saveDraft(recordId, cleanDto, mockUser);

        assertEquals("AUTO_APPROVED_CLEAN", updated.getStatus(), "Clean edit should transition status to AUTO_APPROVED_CLEAN");
        assertNull(updated.getValidationWarnings(), "Validation warnings should be cleared");
    }

    @Test
    @DisplayName("Approve & Generate: throws exception when Cloudinary service fails or missing")
    public void testApproveAndGenerate_FailsWhenCloudinaryFails() throws Exception {
        Long recordId = 1L;
        AiQuoteConversion conversion = new AiQuoteConversion();
        conversion.setId(recordId);
        conversion.setStatus("NEEDS_REVIEW");
        conversion.setVersion(1);

        when(repository.findById(recordId)).thenReturn(Optional.of(conversion));
        when(flowForceQuotePdfService.generateFlowForceQuotePdf(any())).thenReturn(new byte[]{1, 2, 3});
        // Simulate missing or failing Cloudinary service
        when(cloudinaryService.uploadQuotePdf(any(), anyString())).thenThrow(new IllegalStateException("Cloudinary down"));

        AiQuotationEditDto editDto = new AiQuotationEditDto();
        editDto.setQuotationNumber("FF-101");

        assertThrows(IllegalStateException.class, () -> {
            aiQuoteConversionService.approveAndGenerate(recordId, editDto, mockUser);
        }, "Should throw IllegalStateException when PDF upload fails");

        verify(repository).save(argThat(c -> "FAILED".equals(c.getStatus())));
    }
}
