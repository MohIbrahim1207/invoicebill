package com.billing.invoicehub.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String poNumber;
    private boolean active;
    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser vendor;
    private BigDecimal amountInvoiced;
    private BigDecimal poAmount;
    private String currency;
    private BigDecimal amount;
    @ManyToOne(fetch = FetchType.LAZY)
    private Client client;
    private LocalDate dueDate;
    private String notes;
    private String poStatus;
    private String description;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public AppUser getVendor() { return vendor; }
    public void setVendor(AppUser vendor) { this.vendor = vendor; }
    public BigDecimal getAmountInvoiced() { return amountInvoiced; }
    public void setAmountInvoiced(BigDecimal amountInvoiced) { this.amountInvoiced = amountInvoiced; }
    public BigDecimal getPoAmount() { return poAmount; }
    public void setPoAmount(BigDecimal poAmount) { this.poAmount = poAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getPoStatus() { return poStatus; }
    public void setPoStatus(String poStatus) { this.poStatus = poStatus; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}



