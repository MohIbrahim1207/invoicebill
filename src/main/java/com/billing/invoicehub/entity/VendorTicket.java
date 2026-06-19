package com.billing.invoicehub.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
public class VendorTicket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser owner;

    @ManyToOne(fetch = FetchType.LAZY)
    private Client client;

    private String invoiceNo;
    private LocalDate invoiceDate;
    private BigDecimal amount;
    private String currency;

    // Changed from fileName to fileUrl
    private String invoiceFileName;
    private String invoiceFileUrl;


    private String supportingDocumentName;
    private String supportingDocumentUrl;

    private String ticketNo;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal total;
    private String poNumber;

    // Changed from Path to URL
    private String taxDocumentUrl;
    private String poCopyUrl;
    private String deliveryNoteUrl;
    private String otherDocumentUrl;

    @Enumerated(EnumType.STRING)
    private TicketStatus statusRequest;

    private LocalDateTime createdAt;

    @jakarta.persistence.Column(length = 2000)
    private String vendorRemarks;

    // Getters and Setters
    public String getVendorRemarks() { return vendorRemarks; }
    public void setVendorRemarks(String vendorRemarks) { this.vendorRemarks = vendorRemarks; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AppUser getVendor() { return vendor; }
    public void setVendor(AppUser vendor) { this.vendor = vendor; }

    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }

    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }

    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }

    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getInvoiceFileName() { return invoiceFileName; }
    public void setInvoiceFileName(String invoiceFileName) { this.invoiceFileName = invoiceFileName; }

    public String getInvoiceFileUrl() { return invoiceFileUrl; }
    public void setInvoiceFileUrl(String invoiceFileUrl) { this.invoiceFileUrl = invoiceFileUrl; }


    public String getSupportingDocumentName() { return supportingDocumentName; }
    public void setSupportingDocumentName(String supportingDocumentName) {
        this.supportingDocumentName = supportingDocumentName;
    }

    public String getSupportingDocumentUrl() { return supportingDocumentUrl; }
    public void setSupportingDocumentUrl(String supportingDocumentUrl) {
        this.supportingDocumentUrl = supportingDocumentUrl;
    }

    public String getTicketNo() { return ticketNo; }
    public void setTicketNo(String ticketNo) { this.ticketNo = ticketNo; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getTax() { return tax; }
    public void setTax(BigDecimal tax) { this.tax = tax; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }

    public String getTaxDocumentUrl() { return taxDocumentUrl; }
    public void setTaxDocumentUrl(String taxDocumentUrl) { this.taxDocumentUrl = taxDocumentUrl; }

    public String getPoCopyUrl() { return poCopyUrl; }
    public void setPoCopyUrl(String poCopyUrl) { this.poCopyUrl = poCopyUrl; }

    public String getDeliveryNoteUrl() { return deliveryNoteUrl; }
    public void setDeliveryNoteUrl(String deliveryNoteUrl) { this.deliveryNoteUrl = deliveryNoteUrl; }

    public String getOtherDocumentUrl() { return otherDocumentUrl; }
    public void setOtherDocumentUrl(String otherDocumentUrl) { this.otherDocumentUrl = otherDocumentUrl; }

    public TicketStatus getStatusRequest() { return statusRequest; }
    public void setStatusRequest(TicketStatus statusRequest) { this.statusRequest = statusRequest; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}


