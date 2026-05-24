/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.controller.AdminPurchaseOrderController
 *  com.billing.invoicehub.dto.PurchaseOrderRequest
 *  com.billing.invoicehub.repository.AppUserRepository
 *  com.billing.invoicehub.repository.ClientRepository
 *  com.billing.invoicehub.service.PurchaseOrderService
 *  jakarta.validation.Valid
 *  org.springframework.stereotype.Controller
 *  org.springframework.ui.Model
 *  org.springframework.validation.BindingResult
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.ModelAttribute
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.servlet.mvc.support.RedirectAttributes
 */
package com.billing.invoicehub.controller;

import com.billing.invoicehub.dto.PurchaseOrderRequest;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.ClientRepository;
import com.billing.invoicehub.service.PurchaseOrderService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
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
@RequestMapping(value={"/admin/purchase-orders"})
public class AdminPurchaseOrderController {
    private final PurchaseOrderService poService;
    private final AppUserRepository userRepository;
    private final ClientRepository clientRepository;

    public AdminPurchaseOrderController(PurchaseOrderService poService, AppUserRepository userRepository, ClientRepository clientRepository) {
        this.poService = poService;
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
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
        return "admin-purchase-order-detail";
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
        List vendors = this.userRepository.findByRoles_NameOrderByIdDesc("ROLE_USER");
        List clients = this.clientRepository.findAll();
        model.addAttribute("vendors", (Object)vendors);
        model.addAttribute("clients", (Object)clients);
        model.addAttribute("purchaseOrderRequest", (Object)request);
        model.addAttribute("minDueDate", (Object)LocalDate.now().toString());
    }
}

