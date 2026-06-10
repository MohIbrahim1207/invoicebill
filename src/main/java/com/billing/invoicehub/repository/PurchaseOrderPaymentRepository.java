package com.billing.invoicehub.repository;

import com.billing.invoicehub.entity.PurchaseOrderPayment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderPaymentRepository extends JpaRepository<PurchaseOrderPayment, Long> {
    List<PurchaseOrderPayment> findByPurchaseOrder_IdOrderByPaymentDateDescCreatedAtDesc(Long purchaseOrderId);
}
