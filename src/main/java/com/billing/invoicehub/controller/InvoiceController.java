package com.billing.invoicehub.controller;

import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.Client;
import com.billing.invoicehub.entity.Invoice;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.ClientRepository;
import com.billing.invoicehub.repository.InvoiceRepository;
import com.billing.invoicehub.service.CloudinaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Controller
public class InvoiceController {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceController.class);

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired(required = false)
    private CloudinaryService cloudinaryService;

    // ─── GET /invoice ────────────────────────────────────────────────────────────

    @GetMapping("/invoice")
    public String invoicePage(Model model, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/login";
        }

        boolean isAdmin = isAdmin();
        List<Client> clients = isAdmin
                ? clientRepository.findAll()
                : clientRepository.findByOwner_Id(currentUser.get().getId());

        // ✅ Use JOIN FETCH queries to avoid LazyInitializationException
        List<Invoice> invoices = isAdmin
                ? invoiceRepository.findAllWithClientOrderByIdDesc()
                : invoiceRepository.findByClientOwnerIdWithClientOrderByIdDesc(currentUser.get().getId());

        model.addAttribute("clients", clients);
        model.addAttribute("invoices", invoices);
        return "invoice";
    }

    // ─── POST /saveInvoice ───────────────────────────────────────────────────────

    @PostMapping("/saveInvoice")
    public String saveInvoice(@ModelAttribute Invoice invoice,
                              @RequestParam(value = "clientName", required = false) String clientName,
                              @RequestParam("invoiceFile") MultipartFile file,
                              RedirectAttributes redirectAttributes) {

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

        String normalizedClientName = clientName.trim();
        Optional<Client> client = resolveInvoiceClient(normalizedClientName, currentUser.get(), isAdmin);

        if (client.isEmpty()) {
            Client newClient = new Client();
            newClient.setCompanyName(normalizedClientName);
            newClient.setOwner(currentUser.get());
            client = Optional.of(clientRepository.save(newClient));
        }

        try {
            if (cloudinaryService == null) {
                redirectAttributes.addFlashAttribute("error", "File upload service is not configured. Please configure Cloudinary credentials.");
                return "redirect:/invoice";
            }

            String fileUrl = cloudinaryService.uploadInvoiceFile(file);
            invoice.setFileName(file.getOriginalFilename());
            invoice.setFileUrl(fileUrl);
            invoice.setClient(client.get());
            if (invoice.getStatus() == null || invoice.getStatus().isBlank()) {
                invoice.setStatus("Pending");
            }

            invoiceRepository.save(invoice);
            redirectAttributes.addFlashAttribute("message", "Invoice uploaded for " + client.get().getCompanyName() + ".");

        } catch (IllegalArgumentException e) {
            logger.error("File validation failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "File validation failed: " + e.getMessage());
            return "redirect:/invoice";
        } catch (IOException e) {
            logger.error("Failed to upload invoice: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to upload invoice file: " + e.getMessage());
            return "redirect:/invoice";
        }

        return "redirect:/invoice";
    }

    // ─── GET /invoice/{id} ───────────────────────────────────────────────────────

    @GetMapping("/invoice/{id}")
    public String viewInvoice(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Invoice> invoice = loadAccessibleInvoice(id, redirectAttributes);
        if (invoice.isEmpty()) {
            return "redirect:/invoice";
        }
        model.addAttribute("invoice", invoice.get());
        return "invoice-detail";
    }

    // ─── GET /invoice/{id}/edit ──────────────────────────────────────────────────

    @GetMapping("/invoice/{id}/edit")
    public String editInvoice(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Invoice> invoice = loadAccessibleInvoice(id, redirectAttributes);
        if (invoice.isEmpty()) {
            return "redirect:/invoice";
        }
        model.addAttribute("invoice", invoice.get());
        model.addAttribute("clients", visibleClients());
        return "invoice-edit";
    }

    // ─── POST /updateInvoice ─────────────────────────────────────────────────────

    @PostMapping("/updateInvoice")
    public String updateInvoice(@ModelAttribute Invoice invoice,
                                @RequestParam(value = "invoiceFile", required = false) MultipartFile file,
                                RedirectAttributes redirectAttributes) {

        Optional<Invoice> existingInvoice = loadAccessibleInvoice(invoice.getId(), redirectAttributes);
        if (existingInvoice.isEmpty()) {
            return "redirect:/invoice";
        }

        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return "redirect:/login";
        }

        boolean isAdmin = isAdmin();
        Invoice inv = existingInvoice.get();
        inv.setInvoiceNumber(invoice.getInvoiceNumber());
        inv.setInvoiceDate(invoice.getInvoiceDate());
        inv.setAmount(invoice.getAmount());

        if (invoice.getClient() == null || invoice.getClient().getId() == null) {
            redirectAttributes.addFlashAttribute("error", "Please select a client.");
            return "redirect:/invoice/" + invoice.getId() + "/edit";
        }

        Optional<Client> selectedClient = isAdmin
                ? clientRepository.findById(invoice.getClient().getId())
                : clientRepository.findById(invoice.getClient().getId())
                  .filter(c -> c.getOwner() != null && c.getOwner().getId().equals(currentUser.get().getId()));

        if (selectedClient.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "You can only use your own clients.");
            return "redirect:/invoice/" + invoice.getId() + "/edit";
        }

        inv.setClient(selectedClient.get());

        if (file != null && !file.isEmpty()) {
            try {
                if (cloudinaryService == null) {
                    redirectAttributes.addFlashAttribute("error", "File upload service is not configured. Please configure Cloudinary credentials.");
                    return "redirect:/invoice/" + invoice.getId() + "/edit";
                }

                String fileUrl = cloudinaryService.uploadInvoiceFile(file);
                inv.setFileName(file.getOriginalFilename());
                inv.setFileUrl(fileUrl);

            } catch (IllegalArgumentException e) {
                redirectAttributes.addFlashAttribute("error", "File validation failed: " + e.getMessage());
                return "redirect:/invoice/" + invoice.getId() + "/edit";
            } catch (IOException e) {
                redirectAttributes.addFlashAttribute("error", "Could not save the invoice file: " + e.getMessage());
                return "redirect:/invoice/" + invoice.getId() + "/edit";
            }
        }

        invoiceRepository.save(inv);
        redirectAttributes.addFlashAttribute("message", "Invoice updated successfully.");
        return "redirect:/invoice";
    }

    // ─── POST /deleteInvoice/{id} ────────────────────────────────────────────────

    @PostMapping("/deleteInvoice/{id}")
    public String deleteInvoice(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Optional<Invoice> invoice = loadAccessibleInvoice(id, redirectAttributes);
        if (invoice.isEmpty()) {
            return "redirect:/invoice";
        }
        String status = invoice.get().getStatus();
        if (status != null && !status.equalsIgnoreCase("Pending")) {
            redirectAttributes.addFlashAttribute("error", "Only pending invoices can be deleted.");
            return "redirect:/invoice";
        }
        invoiceRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("message", "Invoice deleted successfully.");
        return "redirect:/invoice";
    }

    // ─── Private helpers ─────────────────────────────────────────────────────────

    private List<Client> visibleClients() {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty() || isAdmin()) {
            return clientRepository.findAll();
        }
        return clientRepository.findByOwner_Id(currentUser.get().getId());
    }

    private Optional<Invoice> loadAccessibleInvoice(Long id, RedirectAttributes redirectAttributes) {
        Optional<AppUser> currentUser = currentAppUser();
        if (currentUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please log in to continue.");
            return Optional.empty();
        }

        boolean admin = isAdmin();

        // ✅ Use JOIN FETCH to load client and owner eagerly
        Optional<Invoice> invoice = invoiceRepository.findByIdWithClient(id);

        if (invoice.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Invoice not found.");
            return Optional.empty();
        }

        if (admin) {
            return invoice;
        }

        if (invoice.get().getClient() == null
                || invoice.get().getClient().getOwner() == null
                || !invoice.get().getClient().getOwner().getId().equals(currentUser.get().getId())) {
            redirectAttributes.addFlashAttribute("error", "You can only access your own invoices.");
            return Optional.empty();
        }

        return invoice;
    }

    private Optional<AppUser> currentAppUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return Optional.empty();
        }
        return userRepository.findByUsername(authentication.getName());
    }

    private Optional<Client> resolveInvoiceClient(String clientName, AppUser currentUser, boolean admin) {
        if (admin) {
            return clientRepository.findAllByCompanyNameIgnoreCase(clientName).stream().findFirst();
        }
        return clientRepository.findByCompanyNameIgnoreCaseAndOwner_Id(clientName, currentUser.getId());
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