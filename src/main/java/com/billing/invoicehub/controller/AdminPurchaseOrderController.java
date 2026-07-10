package com.billing.invoicehub.controller;

import com.billing.invoicehub.dto.PurchaseOrderRequest;
import com.billing.invoicehub.dto.PurchaseOrderItemRequest;
import com.billing.invoicehub.entity.PurchaseOrder;
import com.billing.invoicehub.service.PurchaseOrderPaymentService;
import com.billing.invoicehub.service.UserService;
import com.billing.invoicehub.service.ClientService;
import com.billing.invoicehub.service.PurchaseOrderPdfService;
import com.billing.invoicehub.service.PurchaseOrderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping(value={"/admin/purchase-orders"})
public class AdminPurchaseOrderController {
    private static final Logger log = LoggerFactory.getLogger(AdminPurchaseOrderController.class);
    private final PurchaseOrderService poService;
    private final PurchaseOrderPaymentService paymentService;
    private final PurchaseOrderPdfService pdfService;
    private final UserService userService;
    private final ClientService clientService;

    public AdminPurchaseOrderController(PurchaseOrderService poService, PurchaseOrderPaymentService paymentService, PurchaseOrderPdfService pdfService, UserService userService, ClientService clientService) {
        this.poService = poService;
        this.paymentService = paymentService;
        this.pdfService = pdfService;
        this.userService = userService;
        this.clientService = clientService;
    }

    @GetMapping
    public String list(Model model) {
        List pos = this.poService.listAll();
        model.addAttribute("purchaseOrders", (Object)pos);
        return "admin-purchase-orders";
    }

    @GetMapping(value={"/create"})
    public String createForm(Model model) {
        this.populateCreateForm(model, new PurchaseOrderRequest());
        return "admin-purchase-orders-create";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute(value="purchaseOrderRequest") PurchaseOrderRequest request, BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            log.warn("Purchase order validation failed for PO number '{}' with {} error(s)", request.getPoNumber(), bindingResult.getErrorCount());
            this.populateCreateForm(model, request);
            return "admin-purchase-orders-create";
        }
        try {
            this.poService.createPO(request);
            redirectAttributes.addFlashAttribute("message", (Object)"Purchase Order created successfully");
            return "redirect:/admin/purchase-orders";
        }
        catch (IllegalArgumentException e) {
            this.populateCreateForm(model, request);
            model.addAttribute("error", (Object)e.getMessage());
            return "admin-purchase-orders-create";
        }
        catch (Exception e) {
            this.populateCreateForm(model, request);
            model.addAttribute("error", (Object)("Failed to create purchase order: " + e.getMessage()));
            return "admin-purchase-orders-create";
        }
    }

    @GetMapping(value={"/{id}"})
    public String detail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional po = this.poService.getPOById(id);
        if (po.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Purchase Order not found");
            return "redirect:/admin/purchase-orders";
        }
        model.addAttribute("purchaseOrder", po.get());
        model.addAttribute("paymentHistory", this.paymentService.listByPurchaseOrderId(id));
        return "admin-purchase-order-detail";
    }

    @GetMapping(value={"/{id}/pdf"})
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        Optional<PurchaseOrder> po = this.poService.getPOById(id);
        if (po.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        byte[] pdf = this.pdfService.generatePurchaseOrderPdf(po.get());
        String filename = "purchase-order-" + sanitizeFilename(po.get().getPoNumber()) + ".pdf";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    @PostMapping(value={"/{id}/deactivate"})
    public String deactivate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            this.poService.deactivatePO(id);
            redirectAttributes.addFlashAttribute("message", (Object)"Purchase Order deactivated");
            return "redirect:/admin/purchase-orders";
        }
        catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", (Object)e.getMessage());
            return "redirect:/admin/purchase-orders/" + id;
        }
    }

    private void populateCreateForm(Model model, PurchaseOrderRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            request.setItems(List.of(new PurchaseOrderItemRequest()));
        }
        List vendors = this.userService.findVendors();
        List clients = this.clientService.findAll();
        model.addAttribute("vendors", (Object)vendors);
        model.addAttribute("clients", (Object)clients);
        model.addAttribute("purchaseOrderRequest", (Object)request);
        model.addAttribute("minDueDate", (Object)LocalDate.now().toString());
    }

    private String sanitizeFilename(String value) {
        if (value == null || value.isBlank()) {
            return "document";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9._-]", "-");
    }
}
