package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.TicketStatus;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.InvoiceRepository;
import com.billing.invoicehub.repository.VendorTicketRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides live counts for navigation badges across admin and vendor sidebars:
 * - Pending invoices on Tagihan Masuk (Billing Intake)
 * - Unverified vendors on Verifikasi Vendor (Vendor Verification)
 * - Open tickets on Tiket Vendor (Vendor Tickets)
 */
@Service
@Transactional(readOnly = true)
public class SidebarBadgeService {

    private final InvoiceRepository invoiceRepository;
    private final VendorTicketRepository vendorTicketRepository;
    private final AppUserRepository userRepository;

    public SidebarBadgeService(InvoiceRepository invoiceRepository,
                               VendorTicketRepository vendorTicketRepository,
                               AppUserRepository userRepository) {
        this.invoiceRepository = invoiceRepository;
        this.vendorTicketRepository = vendorTicketRepository;
        this.userRepository = userRepository;
    }

    /**
     * Count of pending invoices (all pending for admin/accounts, vendor-owned for vendors).
     */
    public long getPendingInvoicesCount() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                return 0;
            }
            boolean isAdminOrAccounts = auth.getAuthorities().stream().anyMatch(a ->
                    "ROLE_ADMIN".equals(a.getAuthority()) ||
                    "ROLE_SUPER_ADMIN".equals(a.getAuthority()) ||
                    "ROLE_ACCOUNTS".equals(a.getAuthority()));

            if (isAdminOrAccounts) {
                return invoiceRepository.countByStatusIgnoreCase("Pending");
            }

            return userRepository.findFirstByUsername(auth.getName())
                    .or(() -> userRepository.findFirstByEmailIgnoreCase(auth.getName()))
                    .map(u -> invoiceRepository.countByStatusIgnoreCaseAndClient_Owner_Id("Pending", u.getId()))
                    .orElse(0L);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Count of unverified registered vendors waiting for review (admin/accounts only).
     */
    public long getUnverifiedVendorsCount() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                return 0;
            }
            boolean isAdminOrAccounts = auth.getAuthorities().stream().anyMatch(a ->
                    "ROLE_ADMIN".equals(a.getAuthority()) ||
                    "ROLE_SUPER_ADMIN".equals(a.getAuthority()) ||
                    "ROLE_ACCOUNTS".equals(a.getAuthority()));

            if (!isAdminOrAccounts) {
                return 0;
            }
            return userRepository.countUnverifiedVendors();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Count of open vendor tickets (all open for admin/accounts, owned open for vendors).
     */
    public long getOpenTicketsCount() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                return 0;
            }
            boolean isAdminOrAccounts = auth.getAuthorities().stream().anyMatch(a ->
                    "ROLE_ADMIN".equals(a.getAuthority()) ||
                    "ROLE_SUPER_ADMIN".equals(a.getAuthority()) ||
                    "ROLE_ACCOUNTS".equals(a.getAuthority()));

            if (isAdminOrAccounts) {
                return vendorTicketRepository.countByStatusRequest(TicketStatus.OPEN);
            }

            return userRepository.findFirstByUsername(auth.getName())
                    .or(() -> userRepository.findFirstByEmailIgnoreCase(auth.getName()))
                    .map(u -> vendorTicketRepository.countByStatusRequestAndOwner_Id(TicketStatus.OPEN, u.getId()))
                    .orElse(0L);
        } catch (Exception e) {
            return 0;
        }
    }
}

