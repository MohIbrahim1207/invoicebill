/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.controller.InvoiceController
 *  com.billing.invoicehub.entity.AppUser
 *  com.billing.invoicehub.entity.Client
 *  com.billing.invoicehub.entity.Invoice
 *  com.billing.invoicehub.repository.AppUserRepository
 *  com.billing.invoicehub.repository.ClientRepository
 *  com.billing.invoicehub.repository.InvoiceRepository
 *  com.billing.invoicehub.service.FileStorageService
 *  org.springframework.beans.factory.annotation.Autowired
 *  org.springframework.security.core.Authentication
 *  org.springframework.security.core.GrantedAuthority
 *  org.springframework.security.core.context.SecurityContextHolder
 *  org.springframework.stereotype.Controller
 *  org.springframework.ui.Model
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.ModelAttribute
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.multipart.MultipartFile
 *  org.springframework.web.servlet.mvc.support.RedirectAttributes
 */
package com.billing.invoicehub.controller;

import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.Client;
import com.billing.invoicehub.entity.Invoice;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.ClientRepository;
import com.billing.invoicehub.repository.InvoiceRepository;
import com.billing.invoicehub.service.FileStorageService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class InvoiceController {
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private FileStorageService fileStorageService;

    @GetMapping(value={"/invoice"})
    public String invoicePage(Model model, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = this.currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please log in to continue.");
            return "redirect:/login";
        }
        boolean isAdmin = this.isAdmin();
        List<Client> clients = isAdmin ? this.clientRepository.findAll() : this.clientRepository.findByOwner_Id(currentUser.get().getId());
        List<Invoice> invoices = isAdmin ? this.invoiceRepository.findAllByOrderByIdDesc() : this.invoiceRepository.findByClient_Owner_IdOrderByIdDesc(currentUser.get().getId());
        model.addAttribute("clients", (Object)clients);
        model.addAttribute("invoices", (Object)invoices);
        return "invoice";
    }

    @PostMapping(value={"/saveInvoice"})
    public String saveInvoice(@ModelAttribute Invoice invoice, @RequestParam(value="clientName", required=false) String clientName, @RequestParam(value="invoiceFile") MultipartFile file, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = this.currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please log in to continue.");
            return "redirect:/login";
        }
        boolean isAdmin = this.isAdmin();
        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please choose an invoice file to upload.");
            return "redirect:/invoice";
        }
        if (clientName == null || clientName.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please enter a client name for this invoice.");
            return "redirect:/invoice";
        }
        String normalizedClientName = clientName.trim();
        Optional<Client> client = this.resolveInvoiceClient(normalizedClientName, currentUser.get(), isAdmin);
        if (client.isEmpty()) {
            Client newClient = new Client();
            newClient.setCompanyName(normalizedClientName);
            newClient.setOwner(currentUser.get());
            client = Optional.of(this.clientRepository.save(newClient));
        }
        try {
            invoice.setFileName(this.fileStorageService.storeInvoiceFile(file));
            invoice.setClient(client.get());
            this.invoiceRepository.save(invoice);
            redirectAttributes.addFlashAttribute("message", (Object)("Invoice uploaded for " + client.get().getCompanyName() + "."));
        }
        catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", (Object)"Could not save the invoice file.");
            return "redirect:/invoice";
        }
        return "redirect:/invoice";
    }

    @GetMapping(value={"/invoice/{id}"})
    public String viewInvoice(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Invoice> invoice = this.loadAccessibleInvoice(id, redirectAttributes);
        if (invoice.isEmpty()) {
            return "redirect:/invoice";
        }
        model.addAttribute("invoice", invoice.get());
        return "invoice-detail";
    }

    @GetMapping(value={"/invoice/{id}/edit"})
    public String editInvoice(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Invoice> invoice = this.loadAccessibleInvoice(id, redirectAttributes);
        if (invoice.isEmpty()) {
            return "redirect:/invoice";
        }
        List clients = this.visibleClients();
        model.addAttribute("invoice", invoice.get());
        model.addAttribute("clients", (Object)clients);
        return "invoice-edit";
    }

    @PostMapping(value={"/updateInvoice"})
    public String updateInvoice(@ModelAttribute Invoice invoice, @RequestParam(value="invoiceFile", required=false) MultipartFile file, RedirectAttributes redirectAttributes) {
        Optional<Client> selectedClient;
        Optional<Invoice> existingInvoice = this.loadAccessibleInvoice(invoice.getId(), redirectAttributes);
        if (existingInvoice.isEmpty()) {
            return "redirect:/invoice";
        }
        Optional<AppUser> currentUser = this.currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please log in to continue.");
            return "redirect:/login";
        }
        boolean isAdmin = this.isAdmin();
        Invoice inv = existingInvoice.get();
        inv.setInvoiceNumber(invoice.getInvoiceNumber());
        inv.setInvoiceDate(invoice.getInvoiceDate());
        inv.setAmount(invoice.getAmount());
        if (invoice.getClient() == null || invoice.getClient().getId() == null) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please select a client.");
            return "redirect:/invoice/" + invoice.getId() + "/edit";
        }
        selectedClient = isAdmin ? this.clientRepository.findById(invoice.getClient().getId()) : this.clientRepository.findById(invoice.getClient().getId()).filter(client -> client.getOwner() != null && client.getOwner().getId().equals(currentUser.get().getId()));
        if (selectedClient.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"You can only use your own clients.");
            return "redirect:/invoice/" + invoice.getId() + "/edit";
        }
        inv.setClient(selectedClient.get());
        if (file != null && !file.isEmpty()) {
            try {
                inv.setFileName(this.fileStorageService.storeInvoiceFile(file));
            }
            catch (IOException e) {
                redirectAttributes.addFlashAttribute("error", (Object)"Could not save the invoice file.");
                return "redirect:/invoice/" + invoice.getId() + "/edit";
            }
        }
        this.invoiceRepository.save(inv);
        redirectAttributes.addFlashAttribute("message", (Object)"Invoice updated successfully.");
        return "redirect:/invoice";
    }

    @PostMapping(value={"/deleteInvoice/{id}"})
    public String deleteInvoice(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Optional<Invoice> invoice = this.loadAccessibleInvoice(id, redirectAttributes);
        if (invoice.isEmpty()) {
            return "redirect:/invoice";
        }
        this.invoiceRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("message", (Object)"Invoice deleted successfully.");
        return "redirect:/invoice";
    }

    private List<Client> visibleClients() {
        Optional<AppUser> currentUser = this.currentAppUser();
        if (currentUser.isEmpty() || this.isAdmin()) {
            return this.clientRepository.findAll();
        }
        return this.clientRepository.findByOwner_Id(currentUser.get().getId());
    }

    private Optional<Invoice> loadAccessibleInvoice(Long id, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = this.currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Please log in to continue.");
            return Optional.empty();
        }
        boolean admin = this.isAdmin();
        Optional<Invoice> invoice = this.invoiceRepository.findById(id);
        if (invoice.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Invoice not found.");
            return Optional.empty();
        }
        if (admin) {
            return invoice;
        }
        if (invoice.get().getClient() == null || invoice.get().getClient().getOwner() == null || !invoice.get().getClient().getOwner().getId().equals(currentUser.get().getId())) {
            redirectAttributes.addFlashAttribute("error", (Object)"You can only access your own invoices.");
            return Optional.empty();
        }
        return invoice;
    }

    private Optional<AppUser> currentAppUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return Optional.empty();
        }
        return this.userRepository.findByUsername(authentication.getName());
    }

    private Optional<Client> resolveInvoiceClient(String clientName, AppUser currentUser, boolean admin) {
        if (admin) {
            return this.clientRepository.findAllByCompanyNameIgnoreCase(clientName).stream().findFirst();
        }
        return this.clientRepository.findByCompanyNameIgnoreCaseAndOwner_Id(clientName, currentUser.getId());
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_ADMIN"::equals);
    }
}

