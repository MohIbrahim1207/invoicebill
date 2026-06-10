/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.dto.PurchaseOrderRequest
 *  jakarta.validation.constraints.FutureOrPresent
 *  jakarta.validation.constraints.NotBlank
 *  jakarta.validation.Valid
 *  jakarta.validation.constraints.NotEmpty
 *  jakarta.validation.constraints.NotNull
 */
package com.billing.invoicehub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PurchaseOrderRequest {
    @NotBlank(message="PO number is required")
    private @NotBlank(message="PO number is required") String poNumber;
    @NotNull(message="Vendor is required")
    private @NotNull(message="Vendor is required") Long vendorId;
    private Long clientId;
    private BigDecimal poAmount;
    @Valid
    @NotEmpty(message = "At least one item is required")
    private List<PurchaseOrderItemRequest> items = new ArrayList<>();
    @NotNull(message="Due date is required")
    @FutureOrPresent(message="Due date cannot be in the past")
    private @NotNull(message="Due date is required") @FutureOrPresent(message="Due date cannot be in the past") LocalDate dueDate;
    private String description;
    private String notes;
    private String poStatus;

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

    public BigDecimal getPoAmount() {
        return this.poAmount;
    }

    public void setPoAmount(BigDecimal poAmount) {
        this.poAmount = poAmount;
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

    public Long getClientId() {
        return this.clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public String getNotes() {
        return this.notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getPoStatus() {
        return this.poStatus;
    }

    public void setPoStatus(String poStatus) {
        this.poStatus = poStatus;
    }

    public List<PurchaseOrderItemRequest> getItems() {
        return this.items;
    }

    public void setItems(List<PurchaseOrderItemRequest> items) {
        this.items = items;
    }
}
