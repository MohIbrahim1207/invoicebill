package com.billing.invoicehub;

import com.billing.invoicehub.controller.UserManagementController;
import com.billing.invoicehub.entity.AppRole;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AppRoleRepository;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.test.context.support.WithMockUser;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.profiles.active=dev",
        "app.seed.enabled=false",
        "rate-limiting.enabled=false",
        "app.security.hibp.enabled=false"
})
@Transactional
public class UserManagementAuditTest {

    @MockBean
    private EmailService emailService;

    @Autowired
    private UserManagementController userManagementController;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AppRoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private AppRole roleAdmin;

    @BeforeEach
    void setUp() {
        roleAdmin = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> {
            AppRole r = new AppRole();
            r.setName("ROLE_ADMIN");
            return roleRepository.save(r);
        });
    }

    @Test
    @WithMockUser(username = "admin", roles = {"SUPER_ADMIN"})
    @DisplayName("Audit should detect users whose passwords match admin123 or vendor123 and auto-force password change")
    void testAuditWeakPasswordsEndpoint() {
        // Create user with admin123
        AppUser weakUser1 = new AppUser();
        weakUser1.setUsername("test_weak_admin");
        weakUser1.setEmail("weak_admin@test.com");
        weakUser1.setPassword(passwordEncoder.encode("admin123"));
        weakUser1.setUserDomain("INTERNAL");
        weakUser1.setEnabled(true);
        weakUser1.setRoles(new java.util.HashSet<>(Set.of(roleAdmin)));
        weakUser1.setForcePasswordChange(false);
        userRepository.save(weakUser1);

        // Create user with vendor123
        AppUser weakUser2 = new AppUser();
        weakUser2.setUsername("test_weak_vendor");
        weakUser2.setEmail("weak_vendor@test.com");
        weakUser2.setPassword(passwordEncoder.encode("vendor123"));
        weakUser2.setUserDomain("VENDOR");
        weakUser2.setEnabled(true);
        weakUser2.setRoles(new java.util.HashSet<>());
        weakUser2.setForcePasswordChange(false);
        userRepository.save(weakUser2);

        // Create user with strong password
        AppUser strongUser = new AppUser();
        strongUser.setUsername("test_strong_user");
        strongUser.setEmail("strong_user@test.com");
        strongUser.setPassword(passwordEncoder.encode("Str0ng#Invoice2026"));
        strongUser.setUserDomain("INTERNAL");
        strongUser.setEnabled(true);
        strongUser.setRoles(new java.util.HashSet<>());
        userRepository.save(strongUser);

        // 1. Run audit preview (GET - read only)
        ResponseEntity<?> previewResponse = userManagementController.auditWeakPasswords();
        assertEquals(200, previewResponse.getStatusCode().value());

        @SuppressWarnings("unchecked")
        Map<String, Object> previewBody = (Map<String, Object>) previewResponse.getBody();
        assertNotNull(previewBody);
        assertTrue((Integer) previewBody.get("totalFlagged") >= 2);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> flagged = (List<Map<String, Object>>) previewBody.get("users");
        boolean foundWeak1 = flagged.stream().anyMatch(u -> "test_weak_admin".equals(u.get("username")));
        boolean foundWeak2 = flagged.stream().anyMatch(u -> "test_weak_vendor".equals(u.get("username")));
        boolean foundStrong = flagged.stream().anyMatch(u -> "test_strong_user".equals(u.get("username")));

        assertTrue(foundWeak1, "Expected test_weak_admin to be flagged");
        assertTrue(foundWeak2, "Expected test_weak_vendor to be flagged");
        assertFalse(foundStrong, "Did not expect test_strong_user to be flagged");

        // Verify matchedPassword is NOT returned and weakDefaultPassword is true
        Map<String, Object> weak1Map = flagged.stream().filter(u -> "test_weak_admin".equals(u.get("username"))).findFirst().orElseThrow();
        assertFalse(weak1Map.containsKey("matchedPassword"), "matchedPassword must not be present in response");
        assertEquals(Boolean.TRUE, weak1Map.get("weakDefaultPassword"));

        // Verify GET preview did NOT mutate forcePasswordChange in DB
        AppUser unmutatedWeak1 = userRepository.findByUsername("test_weak_admin").orElseThrow();
        assertFalse(Boolean.TRUE.equals(unmutatedWeak1.isForcePasswordChange()),
                "GET audit preview must NOT mutate forcePasswordChange");

        // 2. Run POST force-reset
        ResponseEntity<?> resetResponse = userManagementController.forceResetWeakPasswords();
        assertEquals(200, resetResponse.getStatusCode().value());

        // Verify that forcePasswordChange was set to true on the flagged users in database
        AppUser refreshedWeak1 = userRepository.findByUsername("test_weak_admin").orElseThrow();
        assertTrue(Boolean.TRUE.equals(refreshedWeak1.isForcePasswordChange()),
                "Expected forcePasswordChange to be true after POST force-reset");
    }
}
