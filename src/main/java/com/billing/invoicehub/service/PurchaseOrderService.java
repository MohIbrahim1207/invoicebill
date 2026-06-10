/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.dto.PurchaseOrderRequest
 *  com.billing.invoicehub.entity.AppUser
 *  com.billing.invoicehub.entity.Client
 *  com.billing.invoicehub.entity.PurchaseOrder
 *  com.billing.invoicehub.repository.AppUserRepository
 *  com.billing.invoicehub.repository.ClientRepository
 *  com.billing.invoicehub.repository.PurchaseOrderRepository
 *  com.billing.invoicehub.service.PurchaseOrderService
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 */
package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.PurchaseOrderItemRequest;
import com.billing.invoicehub.dto.PurchaseOrderRequest;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.Client;
import com.billing.invoicehub.entity.PurchaseOrder;
import com.billing.invoicehub.entity.PurchaseOrderItem;
import com.billing.invoicehub.entity.PurchaseOrderPaymentStatus;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.ClientRepository;
import com.billing.invoicehub.repository.PurchaseOrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseOrderService {
    private final PurchaseOrderRepository poRepository;
    private final AppUserRepository userRepository;
    private final ClientRepository clientRepository;

    public PurchaseOrderService(PurchaseOrderRepository poRepository, AppUserRepository userRepository, ClientRepository clientRepository) {
        this.poRepository = poRepository;
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
    }

    @Transactional(readOnly=true)
    public List<PurchaseOrder> listAll() {
        return this.poRepository.findWithVendorByActiveOrderByCreatedAtDesc(true);
    }

    @Transactional(readOnly=true)
    public List<PurchaseOrder> listByVendor(Long vendorId) {
        return this.poRepository.findByVendor_IdOrderByCreatedAtDesc(vendorId);
    }

    @Transactional(readOnly=true)
    public Optional<PurchaseOrder> getPOById(Long id) {
        return this.poRepository.findWithVendorById(id);
    }

    @Transactional(readOnly=true)
    public Optional<PurchaseOrder> getPOByNumber(String poNumber) {
        return this.poRepository.findByPoNumber(poNumber);
    }

    @Transactional
    public PurchaseOrder createPO(PurchaseOrderRequest request) {
        String poNumber;
        if (request == null) {
            throw new IllegalArgumentException("Purchase order request is required");
        }
        String string = poNumber = request.getPoNumber() == null ? null : request.getPoNumber().trim();
        if (poNumber == null || poNumber.isEmpty()) {
            throw new IllegalArgumentException("PO number is required");
        }
        List<PurchaseOrderItem> items = this.buildItems(request.getItems());
        BigDecimal poAmount = items.stream()
            .map(PurchaseOrderItem::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        LocalDate dueDate = request.getDueDate();
        if (dueDate == null) {
            throw new IllegalArgumentException("Due date is required");
        }
        if (dueDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Due date cannot be in the past");
        }
        if (this.poRepository.findByPoNumber(poNumber).isPresent()) {
            throw new IllegalArgumentException("PO number already exists: " + poNumber);
        }
        AppUser vendor = this.userRepository.findById(request.getVendorId()).orElseThrow(() -> new IllegalArgumentException("Vendor not found"));
        PurchaseOrder po = new PurchaseOrder();
        po.setPoNumber(poNumber);
        po.setVendor(vendor);
        po.setAmountInvoiced(poAmount);
        po.setPoAmount(poAmount);
        po.setAmount(poAmount);
        po.setPaidAmount(BigDecimal.ZERO);
        po.setBalanceAmount(poAmount);
        po.setPaymentStatus(PurchaseOrderPaymentStatus.UNPAID);
        if (request.getClientId() != null) {
            Client client = this.clientRepository.findById(request.getClientId()).orElseThrow(() -> new IllegalArgumentException("Client not found"));
            po.setClient(client);
        }
        po.setDueDate(dueDate);
        po.setNotes(request.getNotes());
        po.setPoStatus(request.getPoStatus());
        po.setDescription(request.getDescription());
        po.setCreatedAt(LocalDateTime.now());
        po.setActive(true);
        po.setItems(items);
        return this.poRepository.save(po);
    }

    @Transactional(readOnly=true)
    public boolean validatePO(String poNumber) {
        Optional po = this.poRepository.findByPoNumber(poNumber);
        return po.isPresent() && ((PurchaseOrder)po.get()).isActive();
    }

    @Transactional(readOnly=true)
    public boolean validatePOForVendor(String poNumber, Long vendorId) {
        Optional po = this.poRepository.findByPoNumberAndVendor_Id(poNumber, vendorId);
        return po.isPresent() && ((PurchaseOrder)po.get()).isActive();
    }

    @Transactional
    public void deactivatePO(Long id) {
        PurchaseOrder po = this.poRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("PO not found"));
        po.setActive(false);
        this.poRepository.save(po);
    }

    private List<PurchaseOrderItem> buildItems(List<PurchaseOrderItemRequest> itemRequests) {
        if (itemRequests == null || itemRequests.isEmpty()) {
            throw new IllegalArgumentException("At least one purchase order item is required");
        }

        List<PurchaseOrderItem> items = new ArrayList<>();
        for (PurchaseOrderItemRequest itemRequest : itemRequests) {
            if (itemRequest == null) {
                continue;
            }

            String itemName = itemRequest.getItemName() == null ? null : itemRequest.getItemName().trim();
            BigDecimal quantity = itemRequest.getQuantity();
            BigDecimal unitPrice = itemRequest.getUnitPrice();

            if (itemName == null || itemName.isEmpty()) {
                throw new IllegalArgumentException("Item name is required");
            }
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than zero");
            }
            if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Unit price must be greater than zero");
            }

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setItemName(itemName);
            item.setQuantity(quantity.setScale(2, RoundingMode.HALF_UP));
            item.setUnitPrice(unitPrice.setScale(2, RoundingMode.HALF_UP));
            item.setLineTotal(quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP));
            items.add(item);
        }

        if (items.isEmpty()) {
            throw new IllegalArgumentException("At least one purchase order item is required");
        }

        return items;
    }
}
