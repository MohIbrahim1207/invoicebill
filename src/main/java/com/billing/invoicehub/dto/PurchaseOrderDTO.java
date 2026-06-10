/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.dto.PurchaseOrderDTO
 *  com.billing.invoicehub.entity.PurchaseOrder
 */
package com.billing.invoicehub.dto;

import com.billing.invoicehub.entity.PurchaseOrder;
import com.billing.invoicehub.entity.PurchaseOrderPaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class PurchaseOrderDTO {
    private Long id;
    private String poNumber;
    private Long vendorId;
    private String vendorUsername;
    private BigDecimal amountInvoiced;
    private BigDecimal poAmount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;
    private PurchaseOrderPaymentStatus paymentStatus;
    private String currency;
    private LocalDate dueDate;
    private String description;
    private LocalDateTime createdAt;
    private boolean active;

    public static PurchaseOrderDTO fromEntity(PurchaseOrder purchaseOrder) {
        PurchaseOrderDTO dto = new PurchaseOrderDTO();
        dto.setId(purchaseOrder.getId());
        dto.setPoNumber(purchaseOrder.getPoNumber());
        if (purchaseOrder.getVendor() != null) {
            dto.setVendorId(purchaseOrder.getVendor().getId());
            dto.setVendorUsername(purchaseOrder.getVendor().getUsername());
        }
        dto.setAmountInvoiced(purchaseOrder.getAmountInvoiced());
        dto.setPoAmount(purchaseOrder.getPoAmount());
        dto.setTotalAmount(purchaseOrder.getTotalAmount());
        dto.setPaidAmount(purchaseOrder.getPaidAmount());
        dto.setBalanceAmount(purchaseOrder.getBalanceAmount());
        dto.setPaymentStatus(purchaseOrder.getPaymentStatus());
        dto.setCurrency(purchaseOrder.getCurrency());
        dto.setDueDate(purchaseOrder.getDueDate());
        dto.setDescription(purchaseOrder.getDescription());
        dto.setCreatedAt(purchaseOrder.getCreatedAt());
        dto.setActive(purchaseOrder.isActive());
        return dto;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPoNumber() {
        return this.poNumber;
    }

    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    public Long getVendorId() {
        return this.vendorId;
    }

    public void setVendorId(Long vendorId) {
        this.vendorId = vendorId;
    }

    public String getVendorUsername() {
        return this.vendorUsername;
    }

    public void setVendorUsername(String vendorUsername) {
        this.vendorUsername = vendorUsername;
    }

    public BigDecimal getAmountInvoiced() {
        return this.amountInvoiced;
    }

    public void setAmountInvoiced(BigDecimal amountInvoiced) {
        this.amountInvoiced = amountInvoiced;
    }

    public BigDecimal getPoAmount() {
        return this.poAmount;
    }

    public void setPoAmount(BigDecimal poAmount) {
        this.poAmount = poAmount;
    }

    public BigDecimal getTotalAmount() {
        return this.totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getPaidAmount() {
        return this.paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public BigDecimal getBalanceAmount() {
        return this.balanceAmount;
    }

    public void setBalanceAmount(BigDecimal balanceAmount) {
        this.balanceAmount = balanceAmount;
    }

    public PurchaseOrderPaymentStatus getPaymentStatus() {
        return this.paymentStatus;
    }

    public void setPaymentStatus(PurchaseOrderPaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getCurrency() {
        return this.currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getDueDate() {
        return this.dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
