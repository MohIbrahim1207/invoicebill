package com.billing.invoicehub.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;  // Original filename for reference
    private String fileUrl;   // Cloudinary secure URL

    private String invoiceNumber;
    private String invoiceDate;
    private java.math.BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    private Client client;

    // Constructors
    public Invoice() {}

    public Invoice(String fileName, String fileUrl, String invoiceNumber,
                   String invoiceDate, java.math.BigDecimal amount, Client client) {
        this.fileName = fileName;
        this.fileUrl = fileUrl;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.amount = amount;
        this.client = client;
    }

    // Getters and Setters
    public Long getId() { return this.id; }
    public void setId(Long id) { this.id = id; }

    public String getFileName() { return this.fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileUrl() { return this.fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getInvoiceNumber() { return this.invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public String getInvoiceDate() { return this.invoiceDate; }
    public void setInvoiceDate(String invoiceDate) { this.invoiceDate = invoiceDate; }

    public java.math.BigDecimal getAmount() { return this.amount; }
    public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }

    public Client getClient() { return this.client; }
    public void setClient(Client client) { this.client = client; }
}

