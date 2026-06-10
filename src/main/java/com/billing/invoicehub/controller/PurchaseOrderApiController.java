/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.controller.PurchaseOrderApiController
 *  com.billing.invoicehub.entity.PurchaseOrder
 *  com.billing.invoicehub.service.PurchaseOrderService
 *  org.springframework.http.ResponseEntity
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.bind.annotation.RestController
 */
package com.billing.invoicehub.controller;

import com.billing.invoicehub.entity.PurchaseOrder;
import com.billing.invoicehub.service.PurchaseOrderService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/purchase-orders"})
public class PurchaseOrderApiController {
    private final PurchaseOrderService poService;

    public PurchaseOrderApiController(PurchaseOrderService poService) {
        this.poService = poService;
    }

    @GetMapping(value={"/validate"})
    public ResponseEntity<Map<String, Object>> validatePO(@RequestParam String poNumber, @RequestParam(required=false) Long vendorId) {
        HashMap<String, Object> response = new HashMap<String, Object>();
        boolean isValid = vendorId != null ? this.poService.validatePOForVendor(poNumber, vendorId) : this.poService.validatePO(poNumber);
        response.put("valid", isValid);
        response.put("poNumber", poNumber);
        response.put("message", isValid ? "PO is valid" : "PO not found or inactive");
        return ResponseEntity.ok(response);
    }

    @GetMapping(value={"/{poNumber}"})
    public ResponseEntity<Map<String, Object>> getPODetails(@PathVariable String poNumber) {
        HashMap<String, Object> response = new HashMap<String, Object>();
        Optional po = this.poService.getPOByNumber(poNumber);
        if (po.isPresent()) {
            response.put("found", true);
            response.put("poNumber", ((PurchaseOrder)po.get()).getPoNumber());
            response.put("amount", ((PurchaseOrder)po.get()).getAmount());
            response.put("totalAmount", ((PurchaseOrder)po.get()).getTotalAmount());
            response.put("paidAmount", ((PurchaseOrder)po.get()).getPaidAmount());
            response.put("balanceAmount", ((PurchaseOrder)po.get()).getBalanceAmount());
            response.put("paymentStatus", ((PurchaseOrder)po.get()).getPaymentStatus());
            response.put("vendor", ((PurchaseOrder)po.get()).getVendor() != null ? ((PurchaseOrder)po.get()).getVendor().getUsername() : null);
            response.put("active", ((PurchaseOrder)po.get()).isActive());
            response.put("createdAt", ((PurchaseOrder)po.get()).getCreatedAt());
        } else {
            response.put("found", false);
            response.put("message", "PO not found");
        }
        return ResponseEntity.ok(response);
    }
}
