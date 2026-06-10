package com.billing.invoicehub.controller;

import com.billing.invoicehub.dto.PurchaseOrderPaymentRequest;
import com.billing.invoicehub.entity.PurchaseOrder;
import com.billing.invoicehub.service.PurchaseOrderPaymentService;
import com.billing.invoicehub.service.PurchaseOrderService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/purchase-orders/{purchaseOrderId}/payments")
public class PurchaseOrderPaymentController {
    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseOrderPaymentService paymentService;

    public PurchaseOrderPaymentController(PurchaseOrderService purchaseOrderService, PurchaseOrderPaymentService paymentService) {
        this.purchaseOrderService = purchaseOrderService;
        this.paymentService = paymentService;
    }

    @GetMapping("/new")
    public String createForm(@PathVariable Long purchaseOrderId, Model model, RedirectAttributes redirectAttributes) {
        Optional<PurchaseOrder> purchaseOrder = this.purchaseOrderService.getPOById(purchaseOrderId);
        if (purchaseOrder.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Purchase Order not found");
            return "redirect:/admin/purchase-orders";
        }

        PurchaseOrderPaymentRequest request = new PurchaseOrderPaymentRequest();
        request.setPaymentDate(LocalDate.now());
        model.addAttribute("purchaseOrder", purchaseOrder.get());
        model.addAttribute("paymentRequest", request);
        return "admin-purchase-order-payment-form";
    }

    @PostMapping
    public String create(
        @PathVariable Long purchaseOrderId,
        @Valid @ModelAttribute("paymentRequest") PurchaseOrderPaymentRequest request,
        BindingResult bindingResult,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        Optional<PurchaseOrder> purchaseOrder = this.purchaseOrderService.getPOById(purchaseOrderId);
        if (purchaseOrder.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Purchase Order not found");
            return "redirect:/admin/purchase-orders";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("purchaseOrder", purchaseOrder.get());
            return "admin-purchase-order-payment-form";
        }

        try {
            this.paymentService.recordPayment(purchaseOrderId, request);
            redirectAttributes.addFlashAttribute("message", "Payment recorded successfully");
            return "redirect:/admin/purchase-orders/" + purchaseOrderId;
        } catch (IllegalArgumentException ex) {
            model.addAttribute("purchaseOrder", purchaseOrder.get());
            model.addAttribute("error", ex.getMessage());
            return "admin-purchase-order-payment-form";
        } catch (Exception ex) {
            model.addAttribute("purchaseOrder", purchaseOrder.get());
            model.addAttribute("error", "Failed to record payment: " + ex.getMessage());
            return "admin-purchase-order-payment-form";
        }
    }
}
