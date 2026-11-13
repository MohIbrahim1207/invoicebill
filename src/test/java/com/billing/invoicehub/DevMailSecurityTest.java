package com.billing.invoicehub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class DevMailSecurityTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
            "app.security.hibp.enabled=false",
            "spring.profiles.active=prod",
            "spring.datasource.url=jdbc:h2:mem:test_prod_db;DB_CLOSE_DELAY=-1;MODE=MySQL",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "rate-limiting.enabled=false"
    })
    class ProdProfileTests {

        @Autowired
        private ApplicationContext applicationContext;

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("Prod Profile: DevMailController bean is NOT loaded in prod profile")
        void testDevMailControllerNotLoadedInProd() {
            assertFalse(applicationContext.containsBean("devMailController"),
                    "DevMailController must NOT be instantiated when prod profile is active");
        }

        @Test
        @WithMockUser(username = "superadmin", roles = {"SUPER_ADMIN"})
        @DisplayName("Prod Profile: /dev/mail/send-test is unreachable (404) even for SUPER_ADMIN in prod profile")
        void testDevMailEndpointUnreachableInProd() throws Exception {
            mockMvc.perform(get("/dev/mail/send-test").param("to", "test@flow-force.com"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(username = "superadmin", roles = {"SUPER_ADMIN"})
        @DisplayName("Prod Profile: /dev/mail/test is unreachable (404) even for SUPER_ADMIN in prod profile")
        void testDevMailTestEndpointUnreachableInProd() throws Exception {
            mockMvc.perform(get("/dev/mail/test").param("to", "test@flow-force.com"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
            "app.security.hibp.enabled=false",
            "spring.profiles.active=dev",
            "rate-limiting.enabled=false"
    })
    class DevProfileTests {

        @Autowired
        private ApplicationContext applicationContext;

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("Dev Profile: DevMailController bean is loaded in dev profile")
        void testDevMailControllerLoadedInDev() {
            assertTrue(applicationContext.containsBean("devMailController"),
                    "DevMailController should be instantiated when dev profile is active");
        }

        @Test
        @DisplayName("Dev Profile: Unauthenticated requests to /dev/mail/send-test are redirected or unauthorized")
        void testUnauthenticatedDevMailRedirects() throws Exception {
            mockMvc.perform(get("/dev/mail/send-test").param("to", "test@flow-force.com"))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @WithMockUser(username = "vendor1", roles = {"VENDOR"})
        @DisplayName("Dev Profile: VENDOR access to /dev/mail/send-test is strictly forbidden (403)")
        void testVendorForbidden() throws Exception {
            mockMvc.perform(get("/dev/mail/send-test").param("to", "test@flow-force.com"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "admin1", roles = {"ADMIN"})
        @DisplayName("Dev Profile: ADMIN access to /dev/mail/send-test is strictly forbidden (403)")
        void testAdminForbidden() throws Exception {
            mockMvc.perform(get("/dev/mail/send-test").param("to", "test@flow-force.com"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "superadmin", roles = {"SUPER_ADMIN"})
        @DisplayName("Dev Profile: SUPER_ADMIN can invoke /dev/mail/send-test")
        void testSuperAdminAllowedSendTest() throws Exception {
            mockMvc.perform(get("/dev/mail/send-test").param("to", "superadmin@flow-force.com"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "superadmin", roles = {"SUPER_ADMIN"})
        @DisplayName("Dev Profile: SUPER_ADMIN can invoke /dev/mail/test")
        void testSuperAdminAllowedTest() throws Exception {
            mockMvc.perform(get("/dev/mail/test").param("to", "superadmin@flow-force.com"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "superadmin", roles = {"SUPER_ADMIN"})
        @DisplayName("Dev Profile: SUPER_ADMIN can invoke /dev/mail/config")
        void testSuperAdminAllowedConfig() throws Exception {
            mockMvc.perform(get("/dev/mail/config"))
                    .andExpect(status().isOk());
        }
    }
}
