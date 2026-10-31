package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.TicketStatus;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.InvoiceRepository;
import com.billing.invoicehub.repository.VendorTicketRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Provides live counts for navigation badges across staff sidebars:
 * - Pending invoices on Tagihan Masuk (Billing Intake)
 * - Unverified vendors on Verifikasi Vendor (Vendor Verification)
 * - Open tickets on Tiket Vendor (Vendor Tickets)
 *
 * Rules:
 * 1. Badges are visible ONLY to authorized roles (ADMIN, SUPER_ADMIN, ACCOUNTS).
 * 2. NEVER shown to vendors.
 * 3. Counts are cached in memory for 30 seconds to optimize database load.
 */
@Service
@Transactional(readOnly = true)
public class SidebarBadgeService {

    private static final long CACHE_TTL_MS = 30_000L; // 30 seconds

    private final InvoiceRepository invoiceRepository;
    private final VendorTicketRepository vendorTicketRepository;
    private final AppUserRepository userRepository;

    private final AtomicLong pendingInvoicesCached = new AtomicLong(0);
    private final AtomicLong pendingInvoicesLastFetch = new AtomicLong(0);

    private final AtomicLong unverifiedVendorsCached = new AtomicLong(0);
    private final AtomicLong unverifiedVendorsLastFetch = new AtomicLong(0);

    private final AtomicLong openTicketsCached = new AtomicLong(0);
    private final AtomicLong openTicketsLastFetch = new AtomicLong(0);

    public SidebarBadgeService(InvoiceRepository invoiceRepository,
                               VendorTicketRepository vendorTicketRepository,
                               AppUserRepository userRepository) {
        this.invoiceRepository = invoiceRepository;
        this.vendorTicketRepository = vendorTicketRepository;
        this.userRepository = userRepository;
    }

    /**
     * Checks if current user is an authorized staff member (never a vendor).
     */
    private boolean isStaff() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                return false;
            }
            boolean isVendor = auth.getAuthorities().stream().anyMatch(a ->
                    "ROLE_VENDOR".equalsIgnoreCase(a.getAuthority()));
            if (isVendor) {
                return false;
            }
            return auth.getAuthorities().stream().anyMatch(a ->
                    "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()) ||
                    "ROLE_SUPER_ADMIN".equalsIgnoreCase(a.getAuthority()) ||
                    "ROLE_ACCOUNTS".equalsIgnoreCase(a.getAuthority()));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Count of pending invoices (staff only, cached for 30s).
     */
    public long getPendingInvoicesCount() {
        if (!isStaff()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        if (now - pendingInvoicesLastFetch.get() < CACHE_TTL_MS) {
            return pendingInvoicesCached.get();
        }
        synchronized (pendingInvoicesCached) {
            if (now - pendingInvoicesLastFetch.get() < CACHE_TTL_MS) {
                return pendingInvoicesCached.get();
            }
            try {
                long count = invoiceRepository.countByStatusIgnoreCase("Pending");
                pendingInvoicesCached.set(count);
                pendingInvoicesLastFetch.set(now);
                return count;
            } catch (Exception e) {
                return pendingInvoicesCached.get();
            }
        }
    }

    /**
     * Count of unverified registered vendors (staff only, cached for 30s).
     */
    public long getUnverifiedVendorsCount() {
        if (!isStaff()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        if (now - unverifiedVendorsLastFetch.get() < CACHE_TTL_MS) {
            return unverifiedVendorsCached.get();
        }
        synchronized (unverifiedVendorsCached) {
            if (now - unverifiedVendorsLastFetch.get() < CACHE_TTL_MS) {
                return unverifiedVendorsCached.get();
            }
            try {
                long count = userRepository.countUnverifiedVendors();
                unverifiedVendorsCached.set(count);
                unverifiedVendorsLastFetch.set(now);
                return count;
            } catch (Exception e) {
                return unverifiedVendorsCached.get();
            }
        }
    }

    /**
     * Count of open vendor tickets (staff only, cached for 30s).
     */
    public long getOpenTicketsCount() {
        if (!isStaff()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        if (now - openTicketsLastFetch.get() < CACHE_TTL_MS) {
            return openTicketsCached.get();
        }
        synchronized (openTicketsCached) {
            if (now - openTicketsLastFetch.get() < CACHE_TTL_MS) {
                return openTicketsCached.get();
            }
            try {
                long count = vendorTicketRepository.countByStatusRequest(TicketStatus.OPEN);
                openTicketsCached.set(count);
                openTicketsLastFetch.set(now);
                return count;
            } catch (Exception e) {
                return openTicketsCached.get();
            }
        }
    }

    /**
     * Invalidates the 30-second cache (e.g. for testing or after data modifications).
     */
    public void invalidateCache() {
        pendingInvoicesLastFetch.set(0);
        unverifiedVendorsLastFetch.set(0);
        openTicketsLastFetch.set(0);
    }
}
