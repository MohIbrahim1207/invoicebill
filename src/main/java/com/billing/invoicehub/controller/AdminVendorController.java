/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.controller.AdminVendorController
 *  com.billing.invoicehub.entity.AppUser
 *  com.billing.invoicehub.service.VendorRegistrationService
 *  org.springframework.stereotype.Controller
 *  org.springframework.ui.Model
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.servlet.mvc.support.RedirectAttributes
 */
package com.billing.invoicehub.controller;

import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.service.VendorRegistrationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping(value={"/admin/vendors"})
public class AdminVendorController {
    private final VendorRegistrationService vendorRegistrationService;

    public AdminVendorController(VendorRegistrationService vendorRegistrationService) {
        this.vendorRegistrationService = vendorRegistrationService;
    }

    @GetMapping
    public String vendors(Model model) {
        model.addAttribute("vendors", (Object)this.vendorRegistrationService.listVendors());
        return "admin-vendors";
    }

    @GetMapping(value={"/{id}"})
    public String vendorDetail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return this.vendorRegistrationService.getVendor(id).map(vendor -> {
            model.addAttribute("vendor", vendor);
            model.addAttribute("vendorStatus", (Object)this.vendorRegistrationService.getVendorStatus(vendor));
            return "admin-vendor-detail";
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("error", (Object)"Vendor not found.");
            return "redirect:/admin/vendors";
        });
    }

    @PostMapping(value={"/{id}/verify"})
    public String verifyVendor(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            AppUser vendor = this.vendorRegistrationService.verifyVendor(id);
            redirectAttributes.addFlashAttribute("message", (Object)("Vendor verified successfully. Vendor Code: " + vendor.getVendorCode()));
        }
        catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", (Object)ex.getMessage());
        }
        return "redirect:/admin/vendors/" + id;
    }

    @PostMapping(value={"/{id}/reject"})
    public String rejectVendor(@PathVariable Long id, @RequestParam String reason, RedirectAttributes redirectAttributes) {
        try {
            this.vendorRegistrationService.rejectVendor(id, reason);
            redirectAttributes.addFlashAttribute("message", (Object)"Vendor registration rejected and email sent.");
        }
        catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", (Object)ex.getMessage());
        }
        return "redirect:/admin/vendors/" + id;
    }
}

