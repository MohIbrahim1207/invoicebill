package com.billing.invoicehub.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName; // Original filename for reference
    private String fileUrl; // Cloudinary secure URL

    private String invoiceNumber;
    private LocalDate invoiceDate;
    @jakarta.persistence.Column(name = "invoice_date_raw")
    private String invoiceDateRaw;
    private BigDecimal amount;
    private String currency = "IDR";
    private String status = "Pending";

    @ManyToOne(fetch = FetchType.LAZY)
    private Client client;

    @Transient
    private String poNumber;

    public String getPoNumber() {
        return this.poNumber;
    }

    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    // Constructors
    public Invoice() {
    }

    public Invoice(String fileName, String fileUrl, String invoiceNumber,
            LocalDate invoiceDate, BigDecimal amount, Client client) {
        this.fileName = fileName;
        this.fileUrl = fileUrl;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.amount = amount;
        this.client = client;
    }

    // Getters and Setters
    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileName() {
        return this.fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileUrl() {
        return this.fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getInvoiceNumber() {
        return this.invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public LocalDate getInvoiceDate() {
        return this.invoiceDate;
    }

    public void setInvoiceDate(LocalDate invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public BigDecimal getAmount() {
        return this.amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return this.currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Client getClient() {
        return this.client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public String getInvoiceDateRaw() {
        return this.invoiceDateRaw;
    }

    public void setInvoiceDateRaw(String invoiceDateRaw) {
        this.invoiceDateRaw = invoiceDateRaw;
    }
}
