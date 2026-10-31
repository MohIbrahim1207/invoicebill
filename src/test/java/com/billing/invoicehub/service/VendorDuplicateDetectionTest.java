package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.AppUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VendorDuplicateDetectionTest {

    @Test
    @DisplayName("Company name normalization strips legal prefixes, suffixes, punctuation, and extra whitespace")
    void testNormalizeCompanyName() {
        assertEquals("maju bersama", VendorRegistrationService.normalizeCompanyName("PT. Maju Bersama"));
        assertEquals("maju bersama", VendorRegistrationService.normalizeCompanyName("Maju Bersama, CV"));
        assertEquals("maju bersama", VendorRegistrationService.normalizeCompanyName("PT Maju Bersama Tbk."));
        assertEquals("acme logistics", VendorRegistrationService.normalizeCompanyName("Acme Logistics Ltd"));
    }

    @Test
    @DisplayName("Phone normalization standardizes Indonesian +62 to 0 and strips non-digits")
    void testNormalizePhone() {
        assertEquals("08123456789", VendorRegistrationService.normalizePhone("+62 812-3456-789"));
        assertEquals("08123456789", VendorRegistrationService.normalizePhone("08123456789"));
        assertEquals("08123456789", VendorRegistrationService.normalizePhone("(0812) 345-6789"));
    }

    @Test
    @DisplayName("Address normalization handles punctuation and whitespace")
    void testNormalizeAddress() {
        assertEquals("jl sudirman no 10 kav 4 jakarta",
                VendorRegistrationService.normalizeAddress("Jl. Sudirman No. 10, Kav. 4 - Jakarta"));
    }

    @Test
    @DisplayName("Detects duplicate vendors sharing phone, normalized company, or address")
    void testSharesVendorDetails() {
        AppUser existing = new AppUser();
        existing.setId(1L);
        existing.setCompanyName("PT. Sinar Jaya Abadi");
        existing.setPhone("08123456789");
        existing.setAddress("Kawasan Industri Pulogadung Blok A No. 12, Jakarta Timur");

        // Shares company name with different legal form
        AppUser dupeCompany = new AppUser();
        dupeCompany.setId(2L);
        dupeCompany.setCompanyName("CV. Sinar Jaya Abadi");
        dupeCompany.setPhone("08999999999");
        dupeCompany.setAddress("Jl. Berbeda No. 99");
        assertTrue(VendorRegistrationService.sharesVendorDetails(existing, dupeCompany));

        // Shares phone (+62 formatting)
        AppUser dupePhone = new AppUser();
        dupePhone.setId(3L);
        dupePhone.setCompanyName("PT. Lain Sejahtera");
        dupePhone.setPhone("+62 812-3456-789");
        dupePhone.setAddress("Jl. Berbeda No. 99");
        assertTrue(VendorRegistrationService.sharesVendorDetails(existing, dupePhone));

        // Shares address
        AppUser dupeAddress = new AppUser();
        dupeAddress.setId(4L);
        dupeAddress.setCompanyName("PT. Mitra Usaha");
        dupeAddress.setPhone("08555555555");
        dupeAddress.setAddress("kawasan industri pulogadung blok a no 12 jakarta timur");
        assertTrue(VendorRegistrationService.sharesVendorDetails(existing, dupeAddress));

        // Completely unique vendor
        AppUser uniqueVendor = new AppUser();
        uniqueVendor.setId(5L);
        uniqueVendor.setCompanyName("PT. Global Teknologi Mandiri");
        uniqueVendor.setPhone("08777777777");
        uniqueVendor.setAddress("Sudirman Central Business District Gedung Equity Tower Lt 20");
        assertFalse(VendorRegistrationService.sharesVendorDetails(existing, uniqueVendor));
    }

    @Test
    @DisplayName("findDuplicateVendorIds returns all duplicate vendor IDs correctly")
    void testFindDuplicateVendorIds() {
        AppUser v1 = new AppUser();
        v1.setId(10L);
        v1.setCompanyName("PT. Maju Lancar");
        v1.setPhone("08111111111");
        v1.setAddress("Gedung Graha Lt 5");

        AppUser v2 = new AppUser();
        v2.setId(20L);
        v2.setCompanyName("CV Maju Lancar"); // Duplicate of v1
        v2.setPhone("08222222222");
        v2.setAddress("Jl. Lain No 10");

        AppUser v3 = new AppUser();
        v3.setId(30L);
        v3.setCompanyName("PT. Berbeda Total");
        v3.setPhone("08333333333");
        v3.setAddress("Jl. Sudirman 55");

        VendorRegistrationService service = new VendorRegistrationService(null, null, null, null, null, null, null);
        Set<Long> dupes = service.findDuplicateVendorIds(List.of(v1, v2, v3));

        assertEquals(2, dupes.size());
        assertTrue(dupes.contains(10L));
        assertTrue(dupes.contains(20L));
        assertFalse(dupes.contains(30L));
    }
}
