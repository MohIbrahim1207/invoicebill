package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.TicketStatus;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.InvoiceRepository;
import com.billing.invoicehub.repository.VendorTicketRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SidebarBadgeServiceTest {

    private InvoiceRepository invoiceRepository;
    private VendorTicketRepository vendorTicketRepository;
    private AppUserRepository userRepository;
    private SidebarBadgeService badgeService;

    @BeforeEach
    void setUp() {
        invoiceRepository = mock(InvoiceRepository.class);
        vendorTicketRepository = mock(VendorTicketRepository.class);
        userRepository = mock(AppUserRepository.class);
        badgeService = new SidebarBadgeService(invoiceRepository, vendorTicketRepository, userRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Vendors NEVER receive sidebar badges (always return 0)")
    void testVendorsNeverReceiveBadges() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("vendor_user", "password",
                        List.of(new SimpleGrantedAuthority("ROLE_VENDOR")))
        );

        assertEquals(0, badgeService.getPendingInvoicesCount());
        assertEquals(0, badgeService.getUnverifiedVendorsCount());
        assertEquals(0, badgeService.getOpenTicketsCount());

        verifyNoInteractions(invoiceRepository);
        verifyNoInteractions(vendorTicketRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Unauthenticated users get 0 for all badge counts")
    void testUnauthenticatedGetsZero() {
        SecurityContextHolder.clearContext();

        assertEquals(0, badgeService.getPendingInvoicesCount());
        assertEquals(0, badgeService.getUnverifiedVendorsCount());
        assertEquals(0, badgeService.getOpenTicketsCount());

        verifyNoInteractions(invoiceRepository);
    }

    @Test
    @DisplayName("Staff (ROLE_ADMIN) gets counts and caches them for 30 seconds")
    void testStaffCountsAnd30sCache() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin_user", "password",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        when(invoiceRepository.countByStatusIgnoreCase("Pending")).thenReturn(5L);
        when(userRepository.countUnverifiedVendors()).thenReturn(3L);
        when(vendorTicketRepository.countByStatusRequest(TicketStatus.OPEN)).thenReturn(8L);

        // First call triggers repo
        assertEquals(5, badgeService.getPendingInvoicesCount());
        assertEquals(3, badgeService.getUnverifiedVendorsCount());
        assertEquals(8, badgeService.getOpenTicketsCount());

        verify(invoiceRepository, times(1)).countByStatusIgnoreCase("Pending");
        verify(userRepository, times(1)).countUnverifiedVendors();
        verify(vendorTicketRepository, times(1)).countByStatusRequest(TicketStatus.OPEN);

        // Immediate subsequent call within 30s uses cache (no repo invocation)
        assertEquals(5, badgeService.getPendingInvoicesCount());
        assertEquals(3, badgeService.getUnverifiedVendorsCount());
        assertEquals(8, badgeService.getOpenTicketsCount());

        verify(invoiceRepository, times(1)).countByStatusIgnoreCase("Pending");
        verify(userRepository, times(1)).countUnverifiedVendors();
        verify(vendorTicketRepository, times(1)).countByStatusRequest(TicketStatus.OPEN);

        // Invalidate cache triggers reload
        badgeService.invalidateCache();
        assertEquals(5, badgeService.getPendingInvoicesCount());
        verify(invoiceRepository, times(2)).countByStatusIgnoreCase("Pending");
    }
}

