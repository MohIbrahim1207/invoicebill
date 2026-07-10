package com.billing.invoicehub.dto;

import java.math.BigDecimal;
import java.util.List;

public class AiQuotationEditDto {
    private String supplierName;
    private String customerName;
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

    // Getters and Setters
    public String getProductImageBase64() {
        return productImageBase64;
    }

    public void setProductImageBase64(String productImageBase64) {
        this.productImageBase64 = productImageBase64;
    }
    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
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
}
