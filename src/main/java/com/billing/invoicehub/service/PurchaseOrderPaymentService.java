package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.PurchaseOrderPaymentRequest;
import com.billing.invoicehub.entity.PurchaseOrder;
import com.billing.invoicehub.entity.PurchaseOrderPayment;
import com.billing.invoicehub.entity.PurchaseOrderPaymentStatus;
import com.billing.invoicehub.repository.PurchaseOrderPaymentRepository;
import com.billing.invoicehub.repository.PurchaseOrderRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseOrderPaymentService {
    private final PurchaseOrderPaymentRepository paymentRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    public PurchaseOrderPaymentService(PurchaseOrderPaymentRepository paymentRepository, PurchaseOrderRepository purchaseOrderRepository) {
        this.paymentRepository = paymentRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderPayment> listByPurchaseOrderId(Long purchaseOrderId) {
        if (purchaseOrderId == null) {
            return List.of();
        }
        return this.paymentRepository.findByPurchaseOrder_IdOrderByPaymentDateDescCreatedAtDesc(purchaseOrderId);
    }

    @Transactional
    public PurchaseOrderPayment recordPayment(Long purchaseOrderId, PurchaseOrderPaymentRequest request) {
        if (purchaseOrderId == null) {
            throw new IllegalArgumentException("Purchase order is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("Payment request is required");
        }

        BigDecimal amount = request.getAmount();
        if (amount == null) {
            throw new IllegalArgumentException("Payment amount is required");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
        if (request.getPaymentDate() == null) {
            throw new IllegalArgumentException("Payment date is required");
        }

        PurchaseOrder purchaseOrder = this.purchaseOrderRepository.findById(purchaseOrderId)
            .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));

        BigDecimal totalAmount = purchaseOrder.getTotalAmount();
        BigDecimal paidAmount = purchaseOrder.getPaidAmount();
        BigDecimal balanceAmount = purchaseOrder.getBalanceAmount();

        if (amount.compareTo(balanceAmount) > 0) {
            throw new IllegalArgumentException("Payment amount cannot exceed the outstanding balance");
        }

        BigDecimal updatedPaidAmount = paidAmount.add(amount);
        BigDecimal updatedBalanceAmount = totalAmount.subtract(updatedPaidAmount);
        if (updatedBalanceAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Payment amount cannot exceed the purchase order total amount");
        }

        PurchaseOrderPayment payment = new PurchaseOrderPayment();
        payment.setPurchaseOrder(purchaseOrder);
        payment.setAmount(amount);
        payment.setPaymentDate(request.getPaymentDate());
        payment.setRemarks(cleanRemarks(request.getRemarks()));
        payment.setCreatedAt(LocalDateTime.now());

        PurchaseOrderPayment savedPayment = this.paymentRepository.save(payment);

        purchaseOrder.setPaidAmount(updatedPaidAmount);
        purchaseOrder.setBalanceAmount(updatedBalanceAmount);
        purchaseOrder.setPaymentStatus(resolveStatus(updatedPaidAmount, totalAmount));
        this.purchaseOrderRepository.save(purchaseOrder);

        return savedPayment;
    }

    private PurchaseOrderPaymentStatus resolveStatus(BigDecimal paidAmount, BigDecimal totalAmount) {
        if (paidAmount == null || paidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return PurchaseOrderPaymentStatus.UNPAID;
        }
        if (paidAmount.compareTo(totalAmount) < 0) {
            return PurchaseOrderPaymentStatus.PARTIALLY_PAID;
        }
        return PurchaseOrderPaymentStatus.PAID;
    }

    private String cleanRemarks(String remarks) {
        if (remarks == null) {
            return null;
        }
        String trimmed = remarks.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
