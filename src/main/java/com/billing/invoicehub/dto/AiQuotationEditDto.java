package com.billing.invoicehub.dto;

import java.math.BigDecimal;
import java.util.List;

public class AiQuotationEditDto {
    private String supplierName;
    private String customerName;
    private Boolean customerSpecified = true;
    private String customerAddress;
    private String quotationNumber;
    private String date;
    private String machineName;
    private String machineModel;
    private String capacity;
    private String technicalSpecifications;
    private String currency = "USD";
    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal tax;
    private BigDecimal totalAmount;
    private String deliveryTime;
    private String warranty;
    private String paymentTerms;
    private String validity;
    private String notes;
    private String attention;
    private String reference;
    private String subject;
    private String product;
    private String operatingTemperature;
    private String operatingPressure;
    private String requiredFeedPressure;
    private String plungerDiameter;
    private String powerConsumption;
    private String eccentricShaftSpeed;
    private String deliveryDetails;
    private String dimensionsWeight;
    private String pricesDescription;
    private String discountDescription;
    private String deliveryTerms;
    private String termsConditions;
    private List<LineItem> lineItems;
    private List<OptionalItem> optionalItems;
    private String productImageBase64;

    // --- New fields for faithful document reconstruction ---
    /** Full cover letter body paragraphs, extracted verbatim from the vendor's letter (seller voice neutralized). */
    private String coverLetterBody;
    /** Structured, numbered Terms & Conditions clauses extracted verbatim. */
    private List<TermsClause> termsAndConditionsList;
    /** Part A — Machine Scope table rows, if present in the source document. */
    private List<TableRow> partAScope;
    /** Part B — Screens/Impellers/Spares table rows, if present in the source document. */
    private List<TableRow> partBScope;
    // --- Generalized flexible specification and custom table fields ---
    private List<KeyValuePair> specificationAttributes;
    private List<CustomTable> customTables;

    // Getters and Setters for generalized fields
    public List<KeyValuePair> getSpecificationAttributes() { return specificationAttributes; }
    public void setSpecificationAttributes(List<KeyValuePair> specificationAttributes) { this.specificationAttributes = specificationAttributes; }

    public List<CustomTable> getCustomTables() { return customTables; }
    public void setCustomTables(List<CustomTable> customTables) { this.customTables = customTables; }

    // --- New nested class: KeyValuePair ---
    public static class KeyValuePair {
        private String label;
        private String value;

        public KeyValuePair() {}
        public KeyValuePair(String label, String value) {
            this.label = label;
            this.value = value;
        }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    // --- New nested class: CustomTable ---
    public static class CustomTable {
        private String title;
        private List<String> headers;
        private List<TableRow> rows;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public List<String> getHeaders() { return headers; }
        public void setHeaders(List<String> headers) { this.headers = headers; }
        public List<TableRow> getRows() { return rows; }
        public void setRows(List<TableRow> rows) { this.rows = rows; }
    }

    // --- All content images extracted from the source PDF (machine photos, drawings, diagrams). ---
    private List<ExtractedImage> extractedImages;

    // Getters and Setters
    public String getProductImageBase64() {
        return productImageBase64;
    }

    public void setProductImageBase64(String productImageBase64) {
        this.productImageBase64 = productImageBase64;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public Boolean getCustomerSpecified() {
        return customerSpecified != null ? customerSpecified : true;
    }

    public void setCustomerSpecified(Boolean customerSpecified) {
        this.customerSpecified = customerSpecified;
    }

    public String getCustomerAddress() {
        return customerAddress;
    }

    public void setCustomerAddress(String customerAddress) {
        this.customerAddress = customerAddress;
    }
    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getQuotationNumber() {
        return quotationNumber;
    }

    public void setQuotationNumber(String quotationNumber) {
        this.quotationNumber = quotationNumber;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getMachineName() {
        return machineName;
    }

    public void setMachineName(String machineName) {
        this.machineName = machineName;
    }

    public String getMachineModel() {
        return machineModel;
    }

    public void setMachineModel(String machineModel) {
        this.machineModel = machineModel;
    }

    public String getCapacity() {
        return capacity;
    }

    public void setCapacity(String capacity) {
        this.capacity = capacity;
    }

    public String getTechnicalSpecifications() {
        return technicalSpecifications;
    }

    public void setTechnicalSpecifications(String technicalSpecifications) {
        this.technicalSpecifications = technicalSpecifications;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public void setTax(BigDecimal tax) {
        this.tax = tax;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getDeliveryTime() {
        return deliveryTime;
    }

    public void setDeliveryTime(String deliveryTime) {
        this.deliveryTime = deliveryTime;
    }

    public String getWarranty() {
        return warranty;
    }

    public void setWarranty(String warranty) {
        this.warranty = warranty;
    }

    public String getPaymentTerms() {
        return paymentTerms;
    }

    public void setPaymentTerms(String paymentTerms) {
        this.paymentTerms = paymentTerms;
    }

    public String getValidity() {
        return validity;
    }

    public void setValidity(String validity) {
        this.validity = validity;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getAttention() {
        return attention;
    }

    public void setAttention(String attention) {
        this.attention = attention;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public String getOperatingTemperature() {
        return operatingTemperature;
    }

    public void setOperatingTemperature(String operatingTemperature) {
        this.operatingTemperature = operatingTemperature;
    }

    public String getOperatingPressure() {
        return operatingPressure;
    }

    public void setOperatingPressure(String operatingPressure) {
        this.operatingPressure = operatingPressure;
    }

    public String getRequiredFeedPressure() {
        return requiredFeedPressure;
    }

    public void setRequiredFeedPressure(String requiredFeedPressure) {
        this.requiredFeedPressure = requiredFeedPressure;
    }

    public String getPlungerDiameter() {
        return plungerDiameter;
    }

    public void setPlungerDiameter(String plungerDiameter) {
        this.plungerDiameter = plungerDiameter;
    }

    public String getPowerConsumption() {
        return powerConsumption;
    }

    public void setPowerConsumption(String powerConsumption) {
        this.powerConsumption = powerConsumption;
    }

    public String getEccentricShaftSpeed() {
        return eccentricShaftSpeed;
    }

    public void setEccentricShaftSpeed(String eccentricShaftSpeed) {
        this.eccentricShaftSpeed = eccentricShaftSpeed;
    }

    public String getDeliveryDetails() {
        return deliveryDetails;
    }

    public void setDeliveryDetails(String deliveryDetails) {
        this.deliveryDetails = deliveryDetails;
    }

    public String getDimensionsWeight() {
        return dimensionsWeight;
    }

    public void setDimensionsWeight(String dimensionsWeight) {
        this.dimensionsWeight = dimensionsWeight;
    }

    public String getPricesDescription() {
        return pricesDescription;
    }

    public void setPricesDescription(String pricesDescription) {
        this.pricesDescription = pricesDescription;
    }

    public String getDiscountDescription() {
        return discountDescription;
    }

    public void setDiscountDescription(String discountDescription) {
        this.discountDescription = discountDescription;
    }

    public String getDeliveryTerms() {
        return deliveryTerms;
    }

    public void setDeliveryTerms(String deliveryTerms) {
        this.deliveryTerms = deliveryTerms;
    }

    public String getTermsConditions() {
        return termsConditions;
    }

    public void setTermsConditions(String termsConditions) {
        this.termsConditions = termsConditions;
    }

    public List<LineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<LineItem> lineItems) {
        this.lineItems = lineItems;
    }

    public List<OptionalItem> getOptionalItems() {
        return optionalItems;
    }

    public void setOptionalItems(List<OptionalItem> optionalItems) {
        this.optionalItems = optionalItems;
    }

    // Static nested class for LineItem
    public static class LineItem {
        private String itemName;
        private String description;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;

        public String getItemName() {
            return itemName;
        }

        public void setItemName(String itemName) {
            this.itemName = itemName;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public BigDecimal getTotalPrice() {
            return totalPrice;
        }

        public void setTotalPrice(BigDecimal totalPrice) {
            this.totalPrice = totalPrice;
        }
    }

    // Static nested class for OptionalItem
    public static class OptionalItem {
        private String itemName;
        private String description;
        private BigDecimal price;

        public String getItemName() {
            return itemName;
        }

        public void setItemName(String itemName) {
            this.itemName = itemName;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }
    }

    // --- Getters/Setters for new reconstruction fields ---

    public String getCoverLetterBody() { return coverLetterBody; }
    public void setCoverLetterBody(String coverLetterBody) { this.coverLetterBody = coverLetterBody; }

    public List<TermsClause> getTermsAndConditionsList() { return termsAndConditionsList; }
    public void setTermsAndConditionsList(List<TermsClause> termsAndConditionsList) { this.termsAndConditionsList = termsAndConditionsList; }

    public List<TableRow> getPartAScope() { return partAScope; }
    public void setPartAScope(List<TableRow> partAScope) { this.partAScope = partAScope; }

    public List<TableRow> getPartBScope() { return partBScope; }
    public void setPartBScope(List<TableRow> partBScope) { this.partBScope = partBScope; }

    public List<ExtractedImage> getExtractedImages() { return extractedImages; }
    public void setExtractedImages(List<ExtractedImage> extractedImages) { this.extractedImages = extractedImages; }

    // --- New nested class: TermsClause ---
    /** Represents a single numbered Terms & Conditions clause. */
    public static class TermsClause {
        private String number;
        private String title;
        private String text;

        public String getNumber() { return number; }
        public void setNumber(String number) { this.number = number; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    // --- New nested class: TableRow (for Part A / Part B scope tables) ---
    /** Represents a single row in a multi-column scope table. */
    public static class TableRow {
        private List<String> cells;

        public List<String> getCells() { return cells; }
        public void setCells(List<String> cells) { this.cells = cells; }
    }

    // --- New nested class: ExtractedImage ---
    /** Represents a content image extracted from the source PDF with positional metadata. */
    public static class ExtractedImage {
        private int pageNumber;    // 1-based page number in the source PDF
        private float x;           // x position in PDF points
        private float y;           // y position in PDF points (from bottom)
        private float width;       // rendered width in points
        private float height;      // rendered height in points
        private float pageHeight;  // total source page height in points (for normalization)
        private String imageBase64; // PNG-encoded image bytes as Base64

        public int getPageNumber() { return pageNumber; }
        public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }
        public float getX() { return x; }
        public void setX(float x) { this.x = x; }
        public float getY() { return y; }
        public void setY(float y) { this.y = y; }
        public float getWidth() { return width; }
        public void setWidth(float width) { this.width = width; }
        public float getHeight() { return height; }
        public void setHeight(float height) { this.height = height; }
        public float getPageHeight() { return pageHeight; }
        public void setPageHeight(float pageHeight) { this.pageHeight = pageHeight; }
        public String getImageBase64() { return imageBase64; }
        public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }
    }
}
