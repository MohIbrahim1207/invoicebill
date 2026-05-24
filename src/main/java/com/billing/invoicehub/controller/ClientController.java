/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.controller.ClientController
 *  com.billing.invoicehub.entity.Client
 *  com.billing.invoicehub.repository.AppUserRepository
 *  com.billing.invoicehub.repository.ClientRepository
 *  org.springframework.beans.factory.annotation.Autowired
 *  org.springframework.security.core.Authentication
 *  org.springframework.security.core.context.SecurityContextHolder
 *  org.springframework.stereotype.Controller
 *  org.springframework.ui.Model
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.ModelAttribute
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.servlet.mvc.support.RedirectAttributes
 */
package com.billing.invoicehub.controller;

import com.billing.invoicehub.entity.Client;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.ClientRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ClientController {
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private AppUserRepository userRepository;

    @GetMapping(value={"/clients"})
    public String viewClients(Model model) {
        model.addAttribute("clients", (Object)this.clientRepository.findAll());
        return "clients";
    }

    @PostMapping(value={"/saveClient"})
    public String saveClient(@ModelAttribute Client client, RedirectAttributes redirectAttributes) {
        String username = this.currentUsername();
        if (username != null) {
            this.userRepository.findByUsername(username).ifPresent(client::setOwner);
        }
        this.clientRepository.save(client);
        redirectAttributes.addFlashAttribute("message", (Object)"Client saved successfully.");
        return "redirect:/clients";
    }

    @GetMapping(value={"/clients/{id}"})
    public String viewClient(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Client> client = this.clientRepository.findById(id);
        if (client.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Client not found.");
            return "redirect:/clients";
        }
        model.addAttribute("client", client.get());
        return "client-detail";
    }

    @GetMapping(value={"/clients/{id}/edit"})
    public String editClient(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Client> client = this.clientRepository.findById(id);
        if (client.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Client not found.");
            return "redirect:/clients";
        }
        model.addAttribute("client", client.get());
        return "client-edit";
    }

    @PostMapping(value={"/updateClient"})
    public String updateClient(@ModelAttribute Client client, RedirectAttributes redirectAttributes) {
        Optional<Client> existingClient = this.clientRepository.findById(client.getId());
        if (existingClient.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Client not found.");
            return "redirect:/clients";
        }
        Client c = existingClient.get();
        c.setCompanyName(client.getCompanyName());
        c.setGstNumber(client.getGstNumber());
        c.setEmail(client.getEmail());
        c.setPhone(client.getPhone());
        c.setAddress(client.getAddress());
        c.setOwner(existingClient.get().getOwner());
        this.clientRepository.save(c);
        redirectAttributes.addFlashAttribute("message", (Object)"Client updated successfully.");
        return "redirect:/clients";
    }

    @PostMapping(value={"/deleteClient/{id}"})
    public String deleteClient(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Optional<Client> client = this.clientRepository.findById(id);
        if (client.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", (Object)"Client not found.");
            return "redirect:/clients";
        }
        this.clientRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("message", (Object)"Client deleted successfully.");
        return "redirect:/clients";
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }
}

