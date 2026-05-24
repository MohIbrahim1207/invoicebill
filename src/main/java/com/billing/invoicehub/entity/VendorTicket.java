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
    private String invoiceFileName;
    private String supportingDocumentName;
    private String ticketNo;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal total;
    private String poNumber;
    private String taxDocumentPath;
    private String poCopyPath;
    private String deliveryNotePath;
    private String otherDocumentPath;
    @Enumerated(EnumType.STRING)
    private TicketStatus statusRequest;
    private LocalDateTime createdAt;

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
    public String getSupportingDocumentName() { return supportingDocumentName; }
    public void setSupportingDocumentName(String supportingDocumentName) { this.supportingDocumentName = supportingDocumentName; }
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
    public String getTaxDocumentPath() { return taxDocumentPath; }
    public void setTaxDocumentPath(String taxDocumentPath) { this.taxDocumentPath = taxDocumentPath; }
    public String getPoCopyPath() { return poCopyPath; }
    public void setPoCopyPath(String poCopyPath) { this.poCopyPath = poCopyPath; }
    public String getDeliveryNotePath() { return deliveryNotePath; }
    public void setDeliveryNotePath(String deliveryNotePath) { this.deliveryNotePath = deliveryNotePath; }
    public String getOtherDocumentPath() { return otherDocumentPath; }
    public void setOtherDocumentPath(String otherDocumentPath) { this.otherDocumentPath = otherDocumentPath; }
    public TicketStatus getStatusRequest() { return statusRequest; }
    public void setStatusRequest(TicketStatus statusRequest) { this.statusRequest = statusRequest; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}


