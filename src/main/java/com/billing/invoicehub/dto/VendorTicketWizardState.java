/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.dto.VendorTicketWizardState
 */
package com.billing.invoicehub.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

public class VendorTicketWizardState
implements Serializable {
    private Long clientId;
    private String clientName;
    private String invoiceNo;
    private BigDecimal amount;
    private String currency;
    private LocalDate invoiceDate;
    private String invoiceFileName;
    private String invoiceFileOriginalName;
    private String supportingDocumentName;
    private String supportingDocumentOriginalName;
    private String ticketNo;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal total;
    private String poNumber;
    private String taxDocumentName;
    private String taxDocumentOriginalName;
    private String poCopyName;
    private String poCopyOriginalName;
    private String deliveryNoteName;
    private String deliveryNoteOriginalName;
    private String otherDocumentName;
    private String otherDocumentOriginalName;

    public Long getClientId() {
        return this.clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return this.clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getInvoiceNo() {
        return this.invoiceNo;
    }

    public void setInvoiceNo(String invoiceNo) {
        this.invoiceNo = invoiceNo;
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

    public LocalDate getInvoiceDate() {
        return this.invoiceDate;
    }

    public void setInvoiceDate(LocalDate invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public String getInvoiceFileName() {
        return this.invoiceFileName;
    }

    public void setInvoiceFileName(String invoiceFileName) {
        this.invoiceFileName = invoiceFileName;
    }

    public String getInvoiceFileOriginalName() {
        return this.invoiceFileOriginalName;
    }

    public void setInvoiceFileOriginalName(String invoiceFileOriginalName) {
        this.invoiceFileOriginalName = invoiceFileOriginalName;
    }

    public String getSupportingDocumentName() {
        return this.supportingDocumentName;
    }

    public void setSupportingDocumentName(String supportingDocumentName) {
        this.supportingDocumentName = supportingDocumentName;
    }

    public String getSupportingDocumentOriginalName() {
        return this.supportingDocumentOriginalName;
    }

    public void setSupportingDocumentOriginalName(String supportingDocumentOriginalName) {
        this.supportingDocumentOriginalName = supportingDocumentOriginalName;
    }

    public String getTicketNo() {
        return this.ticketNo;
    }

    public void setTicketNo(String ticketNo) {
        this.ticketNo = ticketNo;
    }

    public BigDecimal getSubtotal() {
        return this.subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTax() {
        return this.tax;
    }

    public void setTax(BigDecimal tax) {
        this.tax = tax;
    }

    public BigDecimal getTotal() {
        return this.total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getPoNumber() {
        return this.poNumber;
    }

    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    public String getTaxDocumentName() {
        return this.taxDocumentName;
    }

    public void setTaxDocumentName(String taxDocumentName) {
        this.taxDocumentName = taxDocumentName;
    }

    public String getTaxDocumentOriginalName() {
        return this.taxDocumentOriginalName;
    }

    public void setTaxDocumentOriginalName(String taxDocumentOriginalName) {
        this.taxDocumentOriginalName = taxDocumentOriginalName;
    }

    public String getPoCopyName() {
        return this.poCopyName;
    }

    public void setPoCopyName(String poCopyName) {
        this.poCopyName = poCopyName;
    }

    public String getPoCopyOriginalName() {
        return this.poCopyOriginalName;
    }

    public void setPoCopyOriginalName(String poCopyOriginalName) {
        this.poCopyOriginalName = poCopyOriginalName;
    }

    public String getDeliveryNoteName() {
        return this.deliveryNoteName;
    }

    public void setDeliveryNoteName(String deliveryNoteName) {
        this.deliveryNoteName = deliveryNoteName;
    }

    public String getDeliveryNoteOriginalName() {
        return this.deliveryNoteOriginalName;
    }

    public void setDeliveryNoteOriginalName(String deliveryNoteOriginalName) {
        this.deliveryNoteOriginalName = deliveryNoteOriginalName;
    }

    public String getOtherDocumentName() {
        return this.otherDocumentName;
    }

    public void setOtherDocumentName(String otherDocumentName) {
        this.otherDocumentName = otherDocumentName;
    }

    public String getOtherDocumentOriginalName() {
        return this.otherDocumentOriginalName;
    }

    public void setOtherDocumentOriginalName(String otherDocumentOriginalName) {
        this.otherDocumentOriginalName = otherDocumentOriginalName;
    }
}

