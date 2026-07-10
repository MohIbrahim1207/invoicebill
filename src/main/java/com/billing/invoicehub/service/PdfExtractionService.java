package com.billing.invoicehub.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import net.sourceforge.tess4j.Tesseract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;

@Service
public class PdfExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(PdfExtractionService.class);

    /**
     * Extracts text from a PDF file. Attempts standard text stripping first.
     * If the extracted text is empty or extremely short, falls back to OCR.
     */
    public String extractText(byte[] pdfBytes) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF content is empty");
        }

        String extractedText = "";
        try (ByteArrayInputStream bais = new ByteArrayInputStream(pdfBytes);
             PDDocument document = PDDocument.load(bais)) {
            
            if (!document.isEncrypted()) {
                PDFTextStripper stripper = new PDFTextStripper();
                extractedText = stripper.getText(document);
            } else {
                logger.warn("PDF document is encrypted. Cannot extract text via PDFBox.");
            }
        }

        // Fallback to OCR if the PDF contains little or no text (e.g., scanned image PDF)
        if (extractedText == null || extractedText.trim().length() < 50) {
            logger.info("Extracted text is empty or very short ({} chars). Falling back to OCR extraction.", 
                    extractedText == null ? 0 : extractedText.trim().length());
            String ocrText = extractTextViaOcr(pdfBytes);
            if (ocrText != null && !ocrText.isBlank()) {
                return ocrText;
            }
        }

        return extractedText != null ? extractedText.trim() : "";
    }

    /**
     * Helper to perform OCR using Tess4J.
     * Gracefully catches any linkage or runtime exceptions if Tesseract binaries are not installed.
     */
    private String extractTextViaOcr(byte[] pdfBytes) {
        StringBuilder ocrResult = new StringBuilder();
        try {
            Tesseract tesseract = new Tesseract();
            // Configure datapath if specified in environment
            String tessDataPath = System.getenv("TESSDATA_PREFIX");
            if (tessDataPath != null && !tessDataPath.isBlank()) {
                tesseract.setDatapath(tessDataPath);
            }

            try (ByteArrayInputStream bais = new ByteArrayInputStream(pdfBytes);
                 PDDocument document = PDDocument.load(bais)) {
                
                PDFRenderer pdfRenderer = new PDFRenderer(document);
                int pageCount = document.getNumberOfPages();
                logger.info("Performing OCR on {} pages", pageCount);

                for (int page = 0; page < pageCount; page++) {
                    // Render page at 150 DPI for reasonable quality vs processing time balance
                    BufferedImage image = pdfRenderer.renderImageWithDPI(page, 150, ImageType.RGB);
                    String pageText = tesseract.doOCR(image);
                    ocrResult.append(pageText).append("\n");
                }
            }
            logger.info("OCR text extraction completed successfully.");
            return ocrResult.toString().trim();
        } catch (Throwable t) {
            // Catches UnsatisfiedLinkError, NoClassDefFoundError, and standard exceptions
            logger.warn("OCR Text Extraction failed (Tesseract may not be installed natively on this system): {}", 
                    t.getMessage());
            return "";
        }
    }

    public static class ImagePlacement {
        public byte[] imageBytes;
        public String hash;
        public int pageIndex;
        public float x;
        public float y;
        public float w;
        public float h;
        public float pageHeight;
    }

    /**
     * Extracts the first image matching machine impression dimensions from the PDF,
     * while generically excluding logos and repeating header/footer graphics.
     */
    public byte[] extractProductImage(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return null;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(pdfBytes);
             PDDocument document = PDDocument.load(bais)) {
            
            final java.util.List<ImagePlacement> placements = new java.util.ArrayList<>();
            
            class ImageLocatorEngine extends org.apache.pdfbox.contentstream.PDFStreamEngine {
                private int currentPageIdx = 0;
                private float currentPageHeight = 0;
                
                public ImageLocatorEngine() {
                    super();
                    addOperator(new org.apache.pdfbox.contentstream.operator.state.Save());
                    addOperator(new org.apache.pdfbox.contentstream.operator.state.Restore());
                    addOperator(new org.apache.pdfbox.contentstream.operator.state.Concatenate());
                    addOperator(new org.apache.pdfbox.contentstream.operator.DrawObject());
                }
                
                public void setPageContext(int pageIdx, float pageHeight) {
                    this.currentPageIdx = pageIdx;
                    this.currentPageHeight = pageHeight;
                }
                
                @Override
                protected void processOperator(org.apache.pdfbox.contentstream.operator.Operator operator, java.util.List<org.apache.pdfbox.cos.COSBase> operands) throws IOException {
                    String operation = operator.getName();
                    if ("Do".equals(operation) && !operands.isEmpty()) {
                        org.apache.pdfbox.cos.COSBase object = operands.get(0);
                        if (object instanceof org.apache.pdfbox.cos.COSName) {
                            org.apache.pdfbox.cos.COSName objectName = (org.apache.pdfbox.cos.COSName) object;
                            org.apache.pdfbox.pdmodel.graphics.PDXObject xobject = getResources().getXObject(objectName);
                            if (xobject instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                                org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject image = (org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) xobject;
                                org.apache.pdfbox.util.Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
                                
                                ImagePlacement p = new ImagePlacement();
                                p.pageIndex = this.currentPageIdx;
                                p.pageHeight = this.currentPageHeight;
                                p.x = ctm.getTranslateX();
                                p.y = ctm.getTranslateY();
                                p.w = ctm.getScalingFactorX();
                                p.h = ctm.getScalingFactorY();
                                
                                try {
                                    BufferedImage bufferedImage = image.getImage();
                                    if (bufferedImage != null) {
                                        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                                            ImageIO.write(bufferedImage, "png", baos);
                                            p.imageBytes = baos.toByteArray();
                                            
                                            // Compute hash to identify duplicate/repeating images
                                            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                                            byte[] digest = md.digest(p.imageBytes);
                                            StringBuilder sb = new StringBuilder();
                                            for (byte b : digest) {
                                                sb.append(String.format("%02x", b));
                                            }
                                            p.hash = sb.toString();
                                        }
                                    }
                                } catch (Exception e) {
                                    // ignore corrupt images
                                }
                                
                                if (p.imageBytes != null && p.hash != null) {
                                    placements.add(p);
                                }
                            }
                        }
                    }
                    super.processOperator(operator, operands);
                }
            }
            
            ImageLocatorEngine engine = new ImageLocatorEngine();
            int pageIdx = 0;
            for (PDPage page : document.getPages()) {
                float height = page.getMediaBox().getHeight();
                engine.setPageContext(pageIdx, height);
                engine.processPage(page);
                pageIdx++;
            }
            
            // Count unique pages for each image hash
            java.util.Map<String, java.util.Set<Integer>> hashToPages = new java.util.HashMap<>();
            for (ImagePlacement p : placements) {
                hashToPages.computeIfAbsent(p.hash, k -> new java.util.HashSet<>()).add(p.pageIndex);
            }
            
            // Filter placements to find a genuine product image on Page 1 (pageIndex == 0)
            for (ImagePlacement p : placements) {
                // Criteria 1: Must be on Page 1 (pageIndex == 0)
                if (p.pageIndex != 0) {
                    continue;
                }
                
                // Criteria 2: Must not repeat across multiple pages (indicates a logo/branding/footer)
                java.util.Set<Integer> pages = hashToPages.get(p.hash);
                if (pages != null && pages.size() > 1) {
                    logger.info("Excluding repeating image (logo/branding) with hash: {}", p.hash);
                    continue;
                }
                
                // Criteria 3: Must not be in the top 25% header region of the page
                float topY = p.y + p.h;
                if (topY > p.pageHeight * 0.75) {
                    logger.info("Excluding image in header region: y={}, h={}, pageHeight={}", p.y, p.h, p.pageHeight);
                    continue;
                }
                
                // Criteria 4: Must have reasonable dimensions to be a product photo (e.g. width and height > 100)
                if (p.w < 100 || p.h < 100) {
                    logger.info("Excluding small image (likely an icon or bullet point): w={}, h={}", p.w, p.h);
                    continue;
                }
                
                logger.info("Successfully extracted product image at x={}, y={}, w={}, h={}", p.x, p.y, p.w, p.h);
                return p.imageBytes;
            }
            
        } catch (Exception e) {
            logger.error("Failed to extract product image from PDF: {}", e.getMessage(), e);
        }
        return null;
    }
}
