package com.billing.invoicehub.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Transient;
import jakarta.persistence.CascadeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    @Column(name = "paid_amount", precision = 19, scale = 2)
    private BigDecimal paidAmount;
    @Column(name = "balance_amount", precision = 19, scale = 2)
    private BigDecimal balanceAmount;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 32)
    private PurchaseOrderPaymentStatus paymentStatus;
    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderItem> items = new ArrayList<>();

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

    @Transient
    public BigDecimal getTotalAmount() {
        if (this.poAmount != null) {
            return this.poAmount;
        }
        if (this.amount != null) {
            return this.amount;
        }
        return this.amountInvoiced != null ? this.amountInvoiced : BigDecimal.ZERO;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.poAmount = totalAmount;
        this.amount = totalAmount;
        this.amountInvoiced = totalAmount;
    }

    public BigDecimal getPaidAmount() {
        return this.paidAmount != null ? this.paidAmount : BigDecimal.ZERO;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public BigDecimal getBalanceAmount() {
        if (this.balanceAmount != null) {
            return this.balanceAmount;
        }
        BigDecimal balance = this.getTotalAmount().subtract(this.getPaidAmount());
        return balance.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : balance;
    }

    public void setBalanceAmount(BigDecimal balanceAmount) {
        this.balanceAmount = balanceAmount;
    }

    public PurchaseOrderPaymentStatus getPaymentStatus() {
        if (this.paymentStatus != null) {
            return this.paymentStatus;
        }
        BigDecimal paid = this.getPaidAmount();
        BigDecimal total = this.getTotalAmount();
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            return PurchaseOrderPaymentStatus.UNPAID;
        }
        if (paid.compareTo(total) < 0) {
            return PurchaseOrderPaymentStatus.PARTIALLY_PAID;
        }
        return PurchaseOrderPaymentStatus.PAID;
    }

    public void setPaymentStatus(PurchaseOrderPaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public List<PurchaseOrderItem> getItems() {
        return this.items;
    }

    public void setItems(List<PurchaseOrderItem> items) {
        this.items.clear();
        if (items != null) {
            for (PurchaseOrderItem item : items) {
                this.addItem(item);
            }
        }
    }

    public void addItem(PurchaseOrderItem item) {
        if (item != null) {
            item.setPurchaseOrder(this);
            this.items.add(item);
        }
    }
}



