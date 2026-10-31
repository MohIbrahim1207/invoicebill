package com.billing.invoicehub;

import com.billing.invoicehub.entity.AppRole;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AppRoleRepository;
import com.billing.invoicehub.repository.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limiting.enabled=false"
})
class DocumentAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AppRoleRepository roleRepository;

    private AppUser vendor1;
    private AppUser vendor2;

    @BeforeEach
    void setUp() {
        AppRole roleVendor = roleRepository.findByName("ROLE_VENDOR")
                .orElseGet(() -> {
                    AppRole r = new AppRole();
                    r.setName("ROLE_VENDOR");
                    return roleRepository.save(r);
                });

        vendor1 = userRepository.findByUsername("doc_vendor_one").orElseGet(() -> {
            AppUser u = new AppUser();
            u.setUsername("doc_vendor_one");
            u.setEmail("doc1@vendor.test");
            u.setPassword("ValidPass123!@#");
            u.setUserDomain("VENDOR");
            u.setRoles(Set.of(roleVendor));
            u.setGstDocumentUrl("https://res.cloudinary.com/test/image/upload/v1/doc1_gst.pdf");
            return userRepository.save(u);
        });

        vendor2 = userRepository.findByUsername("doc_vendor_two").orElseGet(() -> {
            AppUser u = new AppUser();
            u.setUsername("doc_vendor_two");
            u.setEmail("doc2@vendor.test");
            u.setPassword("ValidPass123!@#");
            u.setUserDomain("VENDOR");
            u.setRoles(Set.of(roleVendor));
            u.setGstDocumentUrl("https://res.cloudinary.com/test/image/upload/v1/doc2_gst.pdf");
            return userRepository.save(u);
        });
    }

    @AfterEach
    void tearDown() {
        if (vendor1 != null && vendor1.getId() != null) {
            userRepository.deleteById(vendor1.getId());
        }
        if (vendor2 != null && vendor2.getId() != null) {
            userRepository.deleteById(vendor2.getId());
        }
    }

    @Test
    @DisplayName("Staff (ADMIN) can access any vendor document")
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    void staffCanAccessAnyDocument() throws Exception {
        mockMvc.perform(get("/documents/view")
                        .param("url", vendor1.getGstDocumentUrl()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Staff (ACCOUNTS) can access any vendor document")
    @WithMockUser(username = "accounts_user", roles = {"ACCOUNTS"})
    void accountsCanAccessAnyDocument() throws Exception {
        mockMvc.perform(get("/documents/view")
                        .param("url", vendor1.getGstDocumentUrl()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Staff (SUPER_ADMIN) can access any vendor document")
    @WithMockUser(username = "super_admin_user", roles = {"SUPER_ADMIN"})
    void superAdminCanAccessAnyDocument() throws Exception {
        mockMvc.perform(get("/documents/view")
                        .param("url", vendor1.getGstDocumentUrl()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Vendor can access their own document")
    @WithMockUser(username = "doc_vendor_one", roles = {"VENDOR"})
    void vendorCanAccessOwnDocument() throws Exception {
        mockMvc.perform(get("/documents/view")
                        .param("url", vendor1.getGstDocumentUrl()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Vendor is forbidden from accessing another vendor's document")
    @WithMockUser(username = "doc_vendor_one", roles = {"VENDOR"})
    void vendorCannotAccessOtherVendorDocument() throws Exception {
        mockMvc.perform(get("/documents/view")
                        .param("url", vendor2.getGstDocumentUrl()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Anonymous access to /documents/view is redirected to login")
    void anonymousCannotAccessDocuments() throws Exception {
        mockMvc.perform(get("/documents/view")
                        .param("url", vendor1.getGstDocumentUrl()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/vendor/login")));
    }
}
