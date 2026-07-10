package com.billing.invoicehub.controller;

import com.billing.invoicehub.dto.ClientDto;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.service.ClientService;
import com.billing.invoicehub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
public class ClientController {

    private final ClientService clientService;
    private final UserService userService;

    public ClientController(ClientService clientService, UserService userService) {
        this.clientService = clientService;
        this.userService = userService;
    }

    @GetMapping("/clients")
    public String viewClients(Model model) {
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("client", new ClientDto());
        return "clients";
    }

    @PostMapping("/saveClient")
    public String saveClient(@Valid @ModelAttribute("client") ClientDto clientDto, 
                             BindingResult bindingResult, 
                             RedirectAttributes redirectAttributes, 
                             Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("clients", clientService.findAll());
            return "clients";
        }
        
        String username = currentUsername();
        AppUser owner = null;
        if (username != null) {
            owner = userService.findByUsername(username).orElse(null);
        }
        
        clientService.save(clientDto, owner);
        redirectAttributes.addFlashAttribute("message", "Client saved successfully.");
        return "redirect:/clients";
    }

    @GetMapping("/clients/{id}")
    public String viewClient(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<ClientDto> client = clientService.findById(id);
        if (client.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Client not found.");
            return "redirect:/clients";
        }
        model.addAttribute("client", client.get());
        return "client-detail";
    }

    @GetMapping("/clients/{id}/edit")
    public String editClient(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<ClientDto> client = clientService.findById(id);
        if (client.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Client not found.");
            return "redirect:/clients";
        }
        model.addAttribute("client", client.get());
        return "client-edit";
    }

    @PostMapping("/updateClient")
    public String updateClient(@Valid @ModelAttribute("client") ClientDto clientDto, 
                               BindingResult bindingResult, 
                               RedirectAttributes redirectAttributes, 
                               Model model) {
        if (bindingResult.hasErrors()) {
            return "client-edit";
        }
        
        Optional<ClientDto> updated = clientService.update(clientDto.getId(), clientDto);
        if (updated.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Client not found.");
            return "redirect:/clients";
        }
        
        redirectAttributes.addFlashAttribute("message", "Client updated successfully.");
        return "redirect:/clients";
    }

    @PostMapping("/deleteClient/{id}")
    public String deleteClient(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        boolean deleted = clientService.delete(id);
        if (!deleted) {
            redirectAttributes.addFlashAttribute("error", "Client not found.");
            return "redirect:/clients";
        }
        redirectAttributes.addFlashAttribute("message", "Client deleted successfully.");
        return "redirect:/clients";
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }
}
