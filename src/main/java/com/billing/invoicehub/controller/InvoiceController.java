package com.billing.invoicehub.controller;

import com.billing.invoicehub.dto.InvoiceDto;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.Client;
import com.billing.invoicehub.entity.Invoice;
import com.billing.invoicehub.dto.ClientDto;
import com.billing.invoicehub.service.ClientService;
import com.billing.invoicehub.service.InvoiceService;
import com.billing.invoicehub.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class InvoiceController {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceController.class);

    private final InvoiceService invoiceService;
    private final ClientService clientService;
    private final UserService userService;

    public InvoiceController(InvoiceService invoiceService, ClientService clientService, UserService userService) {
        this.invoiceService = invoiceService;
        this.clientService = clientService;
        this.userService = userService;
    }

    // ─── GET /invoice ────────────────────────────────────────────────────────────

    @GetMapping("/invoice")
    public String invoicePage(Model model, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/login";
        }

        boolean isAdmin = isAdmin();
        List<ClientDto> clients = isAdmin
                ? clientService.findAll()
                : clientService.findByOwnerId(currentUser.get().getId());

        List<Invoice> invoices = invoiceService.getInvoices(currentUser.get(), isAdmin);

        model.addAttribute("clients", clients);
        model.addAttribute("invoices", invoices);
        model.addAttribute("invoice", new InvoiceDto());
        return "invoice";
    }

    // ─── POST /saveInvoice ───────────────────────────────────────────────────────

    @PostMapping("/saveInvoice")
    public String saveInvoice(@Valid @ModelAttribute("invoice") InvoiceDto invoiceDto,
                              BindingResult bindingResult,
                              @RequestParam(value = "clientName", required = false) String clientName,
                              @RequestParam("invoiceFile") MultipartFile file,
                              RedirectAttributes redirectAttributes,
                              Model model) {

        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/login";
        }

        boolean isAdmin = isAdmin();

        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please choose an invoice file to upload.");
            return "redirect:/invoice";
        }

        if (clientName == null || clientName.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please enter a client name for this invoice.");
            return "redirect:/invoice";
        }

        invoiceDto.setClientName(clientName);

        if (bindingResult.hasErrors()) {
            List<ClientDto> clients = isAdmin
                    ? clientService.findAll()
                    : clientService.findByOwnerId(currentUser.get().getId());
            List<Invoice> invoices = invoiceService.getInvoices(currentUser.get(), isAdmin);

            model.addAttribute("clients", clients);
            model.addAttribute("invoices", invoices);
            model.addAttribute("error", "Validation failed. Please check the entered fields.");
            return "invoice";
        }

        try {
            invoiceService.saveInvoice(invoiceDto, file, currentUser.get(), isAdmin);
            redirectAttributes.addFlashAttribute("message", "Invoice uploaded for " + clientName.trim() + ".");
        } catch (IllegalArgumentException e) {
            logger.error("File validation failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "File validation failed: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to upload invoice: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to upload invoice file: " + e.getMessage());
        }

        return "redirect:/invoice";
    }

    // ─── GET /invoice/{id} ───────────────────────────────────────────────────────

    @GetMapping("/invoice/{id}")
    public String viewInvoice(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/login";
        }

        Optional<Invoice> invoiceOpt = invoiceService.getInvoiceEntity(id, currentUser.get(), isAdmin());
        if (invoiceOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Invoice not found.");
            return "redirect:/invoice";
        }
        
        model.addAttribute("invoice", invoiceOpt.get());
        return "invoice-detail";
    }

    // ─── GET /invoice/{id}/edit ──────────────────────────────────────────────────

    @GetMapping("/invoice/{id}/edit")
    public String editInvoice(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/login";
        }

        Optional<Invoice> invoiceOpt = invoiceService.getInvoiceEntity(id, currentUser.get(), isAdmin());
        if (invoiceOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Invoice not found.");
            return "redirect:/invoice";
        }

        model.addAttribute("invoice", invoiceOpt.get());
        model.addAttribute("clients", visibleClients());
        return "invoice-edit";
    }

    // ─── POST /updateInvoice ─────────────────────────────────────────────────────

    @PostMapping("/updateInvoice")
    public String updateInvoice(@Valid @ModelAttribute("invoice") InvoiceDto invoiceDto,
                                BindingResult bindingResult,
                                @RequestParam(value = "invoiceFile", required = false) MultipartFile file,
                                RedirectAttributes redirectAttributes,
                                Model model) {

        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/login";
        }

        boolean isAdmin = isAdmin();

        if (bindingResult.hasErrors()) {
            model.addAttribute("clients", visibleClients());
            // Put the invoice back into the model as an Entity so the Thymeleaf template edit page binds properly
            Optional<Invoice> existingInvoice = invoiceService.getInvoiceEntity(invoiceDto.getId(), currentUser.get(), isAdmin);
            existingInvoice.ifPresent(invoice -> model.addAttribute("invoice", invoice));
            return "invoice-edit";
        }

        try {
            invoiceService.updateInvoice(invoiceDto.getId(), invoiceDto, file, currentUser.get(), isAdmin);
            redirectAttributes.addFlashAttribute("message", "Invoice updated successfully.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "Validation failed: " + e.getMessage());
            return "redirect:/invoice/" + invoiceDto.getId() + "/edit";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Could not save the invoice file: " + e.getMessage());
            return "redirect:/invoice/" + invoiceDto.getId() + "/edit";
        }

        return "redirect:/invoice";
    }

    // ─── POST /deleteInvoice/{id} ────────────────────────────────────────────────

    @PostMapping("/deleteInvoice/{id}")
    public String deleteInvoice(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/login";
        }

        try {
            boolean deleted = invoiceService.deleteInvoice(id, currentUser.get(), isAdmin());
            if (deleted) {
                redirectAttributes.addFlashAttribute("message", "Invoice deleted successfully.");
            } else {
                redirectAttributes.addFlashAttribute("error", "Invoice not found.");
            }
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/invoice";
    }

    // ─── Private helpers ─────────────────────────────────────────────────────────

    private List<ClientDto> visibleClients() {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty() || isAdmin()) {
            return clientService.findAll();
        }
        return clientService.findByOwnerId(currentUser.get().getId());
    }

    private Optional<AppUser> currentAppUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return Optional.empty();
        }
        return userService.findByUsername(authentication.getName());
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}