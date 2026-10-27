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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limiting.enabled=false"
})
class RolePermissionsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AppRoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long vendorId;
    private Long superAdminId;
    private Long normalUserId;

    @BeforeEach
    void setUp() {
        AppRole roleSuperAdmin = getOrCreateRole("ROLE_SUPER_ADMIN");
        AppRole roleAdmin = getOrCreateRole("ROLE_ADMIN");
        AppRole roleAccounts = getOrCreateRole("ROLE_ACCOUNTS");
        AppRole roleVendor = getOrCreateRole("ROLE_VENDOR");

        // Create Super Admin user
        AppUser superAdmin = userRepository.findFirstByUsernameAndUserDomain("perm_superadmin", "INTERNAL")
                .orElseGet(() -> {
                    AppUser u = new AppUser();
                    u.setUsername("perm_superadmin");
                    u.setEmail("perm_superadmin@flow.test");
                    u.setPassword(passwordEncoder.encode("TestPass123!"));
                    u.setUserDomain("INTERNAL");
                    u.setEnabled(true);
                    u.setRoles(Set.of(roleSuperAdmin));
                    return userRepository.save(u);
                });
        superAdminId = superAdmin.getId();

        // Create Normal Accounts user
        AppUser normalUser = userRepository.findFirstByUsernameAndUserDomain("perm_accounts_target", "INTERNAL")
                .orElseGet(() -> {
                    AppUser u = new AppUser();
                    u.setUsername("perm_accounts_target");
                    u.setEmail("perm_accounts_target@flow.test");
                    u.setPassword(passwordEncoder.encode("TestPass123!"));
                    u.setUserDomain("INTERNAL");
                    u.setEnabled(true);
                    u.setRoles(Set.of(roleAccounts));
                    return userRepository.save(u);
                });
        normalUserId = normalUser.getId();

        // Create a Vendor user awaiting verification
        AppUser vendor = userRepository.findFirstByUsernameAndUserDomain("perm_vendor", "VENDOR")
                .orElseGet(() -> {
                    AppUser u = new AppUser();
                    u.setUsername("perm_vendor");
                    u.setEmail("perm_vendor@vendor.test");
                    u.setPassword(passwordEncoder.encode("TestPass123!"));
                    u.setUserDomain("VENDOR");
                    u.setEnabled(true);
                    u.setVerified(false);
                    u.setRegistrationDate(LocalDateTime.now());
                    u.setRoles(Set.of(roleVendor));
                    return userRepository.save(u);
                });
        vendorId = vendor.getId();
    }

    @AfterEach
    void tearDown() {
        // Clean up created test entities if needed
    }

    private AppRole getOrCreateRole(String name) {
        return roleRepository.findByName(name)
                .orElseGet(() -> {
                    AppRole r = new AppRole();
                    r.setName(name);
                    return roleRepository.save(r);
                });
    }

    @Test
    @DisplayName("ROLE_ACCOUNTS cannot reset any password (should get 403 Forbidden)")
    @WithMockUser(username = "accounts_caller", roles = {"ACCOUNTS"})
    void accountsCannotResetPassword() throws Exception {
        mockMvc.perform(post("/admin/users/" + normalUserId + "/reset-password")
                        .with(csrf())
                        .param("password", "NewPass123!"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_ACCOUNTS cannot verify vendor (should get 403 Forbidden)")
    @WithMockUser(username = "accounts_caller", roles = {"ACCOUNTS"})
    void accountsCannotVerifyVendor() throws Exception {
        mockMvc.perform(post("/admin/vendors/" + vendorId + "/verify")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_ACCOUNTS cannot reject vendor (should get 403 Forbidden)")
    @WithMockUser(username = "accounts_caller", roles = {"ACCOUNTS"})
    void accountsCannotRejectVendor() throws Exception {
        mockMvc.perform(post("/admin/vendors/" + vendorId + "/reject")
                        .with(csrf())
                        .param("reason", "Incomplete documents"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_ADMIN cannot reset a SUPER_ADMIN password (should get 403 Forbidden)")
    @WithMockUser(username = "admin_caller", roles = {"ADMIN"})
    void adminCannotResetSuperAdminPassword() throws Exception {
        mockMvc.perform(post("/admin/users/" + superAdminId + "/reset-password")
                        .with(csrf())
                        .param("password", "NewPass123!"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_ADMIN can verify vendor (should succeed with redirect)")
    @WithMockUser(username = "admin_caller", roles = {"ADMIN"})
    void adminCanVerifyVendor() throws Exception {
        mockMvc.perform(post("/admin/vendors/" + vendorId + "/verify")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
    }
}
