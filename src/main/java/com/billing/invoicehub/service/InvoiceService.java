package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.InvoiceDto;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.Client;
import com.billing.invoicehub.entity.Invoice;
import com.billing.invoicehub.repository.ClientRepository;
import com.billing.invoicehub.repository.InvoiceRepository;
import com.billing.invoicehub.util.FileValidationUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final CloudinaryService cloudinaryService;
    private final VendorTicketService vendorTicketService;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          ClientRepository clientRepository,
                          org.springframework.beans.factory.ObjectProvider<CloudinaryService> cloudinaryServiceProvider,
                          VendorTicketService vendorTicketService) {
        this.invoiceRepository = invoiceRepository;
        this.clientRepository = clientRepository;
        this.cloudinaryService = cloudinaryServiceProvider.getIfAvailable();
        this.vendorTicketService = vendorTicketService;
    }

    public List<Invoice> getInvoices(AppUser currentUser, boolean isAdmin) {
        List<Invoice> invoices = isAdmin
                ? invoiceRepository.findAllWithClientOrderByIdDesc()
                : invoiceRepository.findByClientOwnerIdWithClientOrderByIdDesc(currentUser.getId());
        
        vendorTicketService.populatePoNumbers(invoices);
        return invoices;
    }

    public List<InvoiceDto> getInvoicesAsDtos(AppUser currentUser, boolean isAdmin) {
        return getInvoices(currentUser, isAdmin).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public Optional<Invoice> getInvoiceEntity(Long id, AppUser currentUser, boolean isAdmin) {
        Optional<Invoice> invoice = invoiceRepository.findByIdWithClient(id);
        if (invoice.isEmpty()) {
            return Optional.empty();
        }

        if (isAdmin) {
            return invoice;
        }

        Invoice inv = invoice.get();
        if (inv.getClient() == null || inv.getClient().getOwner() == null
                || !inv.getClient().getOwner().getId().equals(currentUser.getId())) {
            return Optional.empty();
        }

        return invoice;
    }

    public Optional<InvoiceDto> getInvoice(Long id, AppUser currentUser, boolean isAdmin) {
        Optional<Invoice> invoice = getInvoiceEntity(id, currentUser, isAdmin);
        if (invoice.isPresent()) {
            vendorTicketService.populatePoNumbers(List.of(invoice.get()));
        }
        return invoice.map(this::convertToDto);
    }

    @Transactional
    public InvoiceDto saveInvoice(InvoiceDto dto, MultipartFile file, AppUser currentUser, boolean isAdmin) throws IOException {
        FileValidationUtil.validateFile(file);

        if (cloudinaryService == null) {
            throw new IllegalStateException("File upload service is not configured. Please configure Cloudinary credentials.");
        }

        if (dto.getClientName() == null || dto.getClientName().trim().isEmpty()) {
            throw new IllegalArgumentException("Please enter a client name for this invoice.");
        }

        String normalizedClientName = dto.getClientName().trim();
        Optional<Client> client = resolveInvoiceClient(normalizedClientName, currentUser, isAdmin);

        if (client.isEmpty()) {
            Client newClient = new Client();
            newClient.setCompanyName(normalizedClientName);
            newClient.setOwner(currentUser);
            client = Optional.of(clientRepository.save(newClient));
        }

        String fileUrl = cloudinaryService.uploadInvoiceFile(file);

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(dto.getInvoiceNumber());
        invoice.setInvoiceDate(dto.getInvoiceDate());
        invoice.setAmount(dto.getAmount());
        invoice.setFileName(file.getOriginalFilename());
        invoice.setFileUrl(fileUrl);
        invoice.setClient(client.get());
        if (dto.getStatus() == null || dto.getStatus().isBlank()) {
            invoice.setStatus("Pending");
        } else {
            invoice.setStatus(dto.getStatus());
        }

        Invoice saved = invoiceRepository.save(invoice);
        return convertToDto(saved);
    }

    @Transactional
    public InvoiceDto updateInvoice(Long id, InvoiceDto dto, MultipartFile file, AppUser currentUser, boolean isAdmin) throws IOException {
        Optional<Invoice> existingInvoice = getInvoiceEntity(id, currentUser, isAdmin);
        if (existingInvoice.isEmpty()) {
            throw new IllegalArgumentException("Invoice not found or access denied.");
        }

        Invoice inv = existingInvoice.get();
        inv.setInvoiceNumber(dto.getInvoiceNumber());
        inv.setInvoiceDate(dto.getInvoiceDate());
        inv.setAmount(dto.getAmount());

        if (dto.getClientId() == null) {
            throw new IllegalArgumentException("Please select a client.");
        }

        Optional<Client> selectedClient = clientRepository.findById(dto.getClientId());
        if (selectedClient.isEmpty()) {
            throw new IllegalArgumentException("Selected client not found.");
        }

        if (!isAdmin && (selectedClient.get().getOwner() == null 
                || !selectedClient.get().getOwner().getId().equals(currentUser.getId()))) {
            throw new IllegalArgumentException("You can only use your own clients.");
        }

        inv.setClient(selectedClient.get());

        if (file != null && !file.isEmpty()) {
            FileValidationUtil.validateFile(file);
            if (cloudinaryService == null) {
                throw new IllegalStateException("File upload service is not configured. Please configure Cloudinary credentials.");
            }
            String fileUrl = cloudinaryService.uploadInvoiceFile(file);
            inv.setFileName(file.getOriginalFilename());
            inv.setFileUrl(fileUrl);
        }

        Invoice saved = invoiceRepository.save(inv);
        return convertToDto(saved);
    }

    @Transactional
    public boolean deleteInvoice(Long id, AppUser currentUser, boolean isAdmin) {
        Optional<Invoice> invoiceOpt = getInvoiceEntity(id, currentUser, isAdmin);
        if (invoiceOpt.isEmpty()) {
            return false;
        }

        Invoice inv = invoiceOpt.get();
        String status = inv.getStatus();
        if (status != null && !status.equalsIgnoreCase("Pending")) {
            throw new IllegalStateException("Only pending invoices can be deleted.");
        }

        invoiceRepository.deleteById(id);
        return true;
    }

    private Optional<Client> resolveInvoiceClient(String clientName, AppUser currentUser, boolean admin) {
        if (admin) {
            return clientRepository.findAllByCompanyNameIgnoreCase(clientName).stream().findFirst();
        }
        return clientRepository.findByCompanyNameIgnoreCaseAndOwner_Id(clientName, currentUser.getId());
    }

    // Manual Mapping Helpers
    public InvoiceDto convertToDto(Invoice invoice) {
        if (invoice == null) {
            return null;
        }
        InvoiceDto dto = new InvoiceDto();
        dto.setId(invoice.getId());
        dto.setFileName(invoice.getFileName());
        dto.setFileUrl(invoice.getFileUrl());
        dto.setInvoiceNumber(invoice.getInvoiceNumber());
        dto.setInvoiceDate(invoice.getInvoiceDate());
        dto.setAmount(invoice.getAmount());
        dto.setCurrency(invoice.getCurrency());
        dto.setStatus(invoice.getStatus());
        if (invoice.getClient() != null) {
            dto.setClientId(invoice.getClient().getId());
            dto.setClientName(invoice.getClient().getCompanyName());
        }
        dto.setPoNumber(invoice.getPoNumber());
        return dto;
    }

    public void updateEntityFromDto(Invoice invoice, InvoiceDto dto) {
        if (invoice == null || dto == null) {
            return;
        }
        invoice.setInvoiceNumber(dto.getInvoiceNumber());
        invoice.setInvoiceDate(dto.getInvoiceDate());
        invoice.setAmount(dto.getAmount());
        invoice.setCurrency(dto.getCurrency());
        invoice.setStatus(dto.getStatus());
    }
}
