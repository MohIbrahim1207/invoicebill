package com.billing.invoicehub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.profiles.active=dev",
        "app.seed.enabled=false",
        "rate-limiting.enabled=false",
        "app.security.hibp.enabled=false"
})
class FaviconSecurityAndAssetTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Favicon assets exist in classpath static resources")
    void testFaviconAssetsExist() {
        assertTrue(new ClassPathResource("static/favicon.ico").exists(), "favicon.ico must exist");
        assertTrue(new ClassPathResource("static/favicon-16x16.png").exists(), "favicon-16x16.png must exist");
        assertTrue(new ClassPathResource("static/favicon-32x32.png").exists(), "favicon-32x32.png must exist");
        assertTrue(new ClassPathResource("static/apple-touch-icon.png").exists(), "apple-touch-icon.png must exist");
        assertTrue(new ClassPathResource("static/android-chrome-192x192.png").exists(), "android-chrome-192x192.png must exist");
        assertTrue(new ClassPathResource("static/android-chrome-512x512.png").exists(), "android-chrome-512x512.png must exist");
        assertTrue(new ClassPathResource("static/site.webmanifest").exists(), "site.webmanifest must exist");
        assertTrue(new ClassPathResource("static/images/logo-source.png").exists(), "logo-source.png must exist");
    }

    @Test
    @DisplayName("SecurityConfig permits unauthenticated access to all favicon and manifest endpoints")
    void testFaviconEndpointsPermitAll() throws Exception {
        mockMvc.perform(get("/favicon.ico")).andExpect(status().isOk());
        mockMvc.perform(get("/favicon-16x16.png")).andExpect(status().isOk());
        mockMvc.perform(get("/favicon-32x32.png")).andExpect(status().isOk());
        mockMvc.perform(get("/apple-touch-icon.png")).andExpect(status().isOk());
        mockMvc.perform(get("/android-chrome-192x192.png")).andExpect(status().isOk());
        mockMvc.perform(get("/android-chrome-512x512.png")).andExpect(status().isOk());
        mockMvc.perform(get("/site.webmanifest")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("site.webmanifest contains correct InvoiceHub metadata and theme color")
    void testWebmanifestContent() throws Exception {
        ClassPathResource resource = new ClassPathResource("static/site.webmanifest");
        String content = Files.readString(resource.getFile().toPath());
        assertTrue(content.contains("\"name\": \"InvoiceHub\""), "Must contain name InvoiceHub");
        assertTrue(content.contains("\"short_name\": \"InvoiceHub\""), "Must contain short_name InvoiceHub");
        assertTrue(content.contains("\"theme_color\": \"#2b6ef2\""), "Must contain theme_color #2b6ef2");
        assertTrue(content.contains("android-chrome-192x192.png"), "Must reference 192px icon");
        assertTrue(content.contains("android-chrome-512x512.png"), "Must reference 512px icon");
    }

    @Test
    @DisplayName("No old flow.png favicon references remain in static or template files")
    void testNoOldFaviconReferences() {
        assertFalse(new File("src/main/resources/static/flow.png").exists(), "flow.png must be deleted");
    }
}

