package com.billing.invoicehub.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

public class VendorTicketWizardState implements Serializable {
    private Long clientId;
    private String clientName;

    @NotBlank(message = "Invoice number is required")
    @Size(max = 50, message = "Invoice number must not exceed 50 characters")
    private String invoiceNo;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", inclusive = true, message = "Amount must be greater than zero")
    private BigDecimal amount;

    @Size(max = 20, message = "Currency must not exceed 20 characters")
    private String currency;

    @NotNull(message = "Invoice date is required")
    private LocalDate invoiceDate;

    // File names (original)
    private String invoiceFileOriginalName;
    private String taxDocumentOriginalName;
    private String poCopyOriginalName;
    private String deliveryNoteOriginalName;
    private String otherDocumentOriginalName;
    private String supportingDocumentOriginalName;

    // File URLs from Cloudinary (replacing old "Name" fields)
    private String invoiceFileUrl;
    private String taxDocumentUrl;
    private String poCopyUrl;
    private String deliveryNoteUrl;
    private String otherDocumentUrl;
    private String supportingDocumentUrl;

    @Size(max = 50, message = "Ticket number must not exceed 50 characters")
    private String ticketNo;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal total;

    @Size(max = 50, message = "PO number must not exceed 50 characters")
    private String poNumber;

    // Getters and Setters
    public Long getClientId() { return this.clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }

    public String getClientName() { return this.clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getInvoiceNo() { return this.invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }

    public BigDecimal getAmount() { return this.amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return this.currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDate getInvoiceDate() { return this.invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }

    public String getInvoiceFileOriginalName() { return this.invoiceFileOriginalName; }
    public void setInvoiceFileOriginalName(String invoiceFileOriginalName) {
        this.invoiceFileOriginalName = invoiceFileOriginalName;
    }

    public String getTaxDocumentOriginalName() { return this.taxDocumentOriginalName; }
    public void setTaxDocumentOriginalName(String taxDocumentOriginalName) {
        this.taxDocumentOriginalName = taxDocumentOriginalName;
    }

    public String getPoCopyOriginalName() { return this.poCopyOriginalName; }
    public void setPoCopyOriginalName(String poCopyOriginalName) {
        this.poCopyOriginalName = poCopyOriginalName;
    }

    public String getDeliveryNoteOriginalName() { return this.deliveryNoteOriginalName; }
    public void setDeliveryNoteOriginalName(String deliveryNoteOriginalName) {
        this.deliveryNoteOriginalName = deliveryNoteOriginalName;
    }

    public String getOtherDocumentOriginalName() { return this.otherDocumentOriginalName; }
    public void setOtherDocumentOriginalName(String otherDocumentOriginalName) {
        this.otherDocumentOriginalName = otherDocumentOriginalName;
    }

    public String getSupportingDocumentOriginalName() { return this.supportingDocumentOriginalName; }
    public void setSupportingDocumentOriginalName(String supportingDocumentOriginalName) {
        this.supportingDocumentOriginalName = supportingDocumentOriginalName;
    }

    public String getInvoiceFileUrl() { return this.invoiceFileUrl; }
    public void setInvoiceFileUrl(String invoiceFileUrl) { this.invoiceFileUrl = invoiceFileUrl; }


    public String getTaxDocumentUrl() { return this.taxDocumentUrl; }
    public void setTaxDocumentUrl(String taxDocumentUrl) { this.taxDocumentUrl = taxDocumentUrl; }

    public String getPoCopyUrl() { return this.poCopyUrl; }
    public void setPoCopyUrl(String poCopyUrl) { this.poCopyUrl = poCopyUrl; }

    public String getDeliveryNoteUrl() { return this.deliveryNoteUrl; }
    public void setDeliveryNoteUrl(String deliveryNoteUrl) { this.deliveryNoteUrl = deliveryNoteUrl; }

    public String getOtherDocumentUrl() { return this.otherDocumentUrl; }
    public void setOtherDocumentUrl(String otherDocumentUrl) { this.otherDocumentUrl = otherDocumentUrl; }

    public String getSupportingDocumentUrl() { return this.supportingDocumentUrl; }
    public void setSupportingDocumentUrl(String supportingDocumentUrl) {
        this.supportingDocumentUrl = supportingDocumentUrl;
    }

    public String getTicketNo() { return this.ticketNo; }
    public void setTicketNo(String ticketNo) { this.ticketNo = ticketNo; }

    public BigDecimal getSubtotal() { return this.subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getTax() { return this.tax; }
    public void setTax(BigDecimal tax) { this.tax = tax; }

    public BigDecimal getTotal() { return this.total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getPoNumber() { return this.poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }

    // Backward compatibility methods (maps old API to new URLs)
    @Deprecated
    public String getInvoiceFileName() { return this.invoiceFileUrl; }
    @Deprecated
    public void setInvoiceFileName(String invoiceFileName) { this.invoiceFileUrl = invoiceFileName; }

    @Deprecated
    public String getTaxDocumentName() { return this.taxDocumentUrl; }
    @Deprecated
    public void setTaxDocumentName(String taxDocumentName) { this.taxDocumentUrl = taxDocumentName; }

    @Deprecated
    public String getPoCopyName() { return this.poCopyUrl; }
    @Deprecated
    public void setPoCopyName(String poCopyName) { this.poCopyUrl = poCopyName; }

    @Deprecated
    public String getDeliveryNoteName() { return this.deliveryNoteUrl; }
    @Deprecated
    public void setDeliveryNoteName(String deliveryNoteName) { this.deliveryNoteUrl = deliveryNoteName; }

    @Deprecated
    public String getOtherDocumentName() { return this.otherDocumentUrl; }
    @Deprecated
    public void setOtherDocumentName(String otherDocumentName) { this.otherDocumentUrl = otherDocumentName; }

    @Deprecated
    public String getSupportingDocumentName() { return this.supportingDocumentUrl; }
    @Deprecated
    public void setSupportingDocumentName(String supportingDocumentName) {
        this.supportingDocumentUrl = supportingDocumentName;
    }
}

