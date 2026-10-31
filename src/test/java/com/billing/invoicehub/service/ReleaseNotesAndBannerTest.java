package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.ReleaseNoteDto;
import com.billing.invoicehub.entity.AppRole;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.SystemSettingRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "app.security.hibp.enabled=false"
})
@Transactional
class ReleaseNotesAndBannerTest {

    @Autowired
    private ReleaseNotesService releaseNotesService;

    @Autowired
    private AppVersionService appVersionService;

    @Autowired
    private MaintenanceBannerService maintenanceBannerService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private SystemSettingRepository systemSettingRepository;

    @Test
    @DisplayName("Release notes file is parsed correctly with bilingual content and current version")
    void testReleaseNotesLoadedAndContainsCurrentVersion() {
        List<ReleaseNoteDto> allNotes = releaseNotesService.getAllNotes();
        assertFalse(allNotes.isEmpty(), "release-notes.json should contain entries");

        // Verify current project version is present in release-notes.json
        String currentVersion = appVersionService.getVersion();
        boolean hasCurrentVersion = allNotes.stream()
                .anyMatch(n -> n.getVersion().equalsIgnoreCase(currentVersion));
        assertTrue(hasCurrentVersion, "release-notes.json must contain entry for current version: " + currentVersion);

        // Verify structure of first entry
        ReleaseNoteDto first = allNotes.get(0);
        assertNotNull(first.getVersion());
        assertNotNull(first.getDate());
        assertNotNull(first.getAudience());
        assertNotNull(first.getTitleEn());
        assertNotNull(first.getTitleId());
        assertFalse(first.getNotesEn().isEmpty(), "English notes should not be empty");
        assertFalse(first.getNotesId().isEmpty(), "Indonesian notes should not be empty");
    }

    @Test
    @DisplayName("Audience filtering separates notes for ADMIN, VENDOR, and ALL")
    void testAudienceFiltering() {
        List<ReleaseNoteDto> adminNotes = releaseNotesService.getNotesForAudience("ADMIN");
        assertNotNull(adminNotes);

        List<ReleaseNoteDto> vendorNotes = releaseNotesService.getNotesForAudience("VENDOR");
        assertNotNull(vendorNotes);

        List<ReleaseNoteDto> allNotes = releaseNotesService.getNotesForAudience("ALL");
        assertNotNull(allNotes);
        assertTrue(allNotes.size() >= adminNotes.size());
    }

    @Test
    @DisplayName("Unread notes detection and acknowledgement updates app_user.last_seen_version")
    void testUnreadNotesAndAcknowledgement() {
        String username = "test_release_user_" + System.currentTimeMillis();
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPassword("SecretPass123!@#");
        user.setUserDomain("INTERNAL");
        user.setEnabled(true);
        user.setLastSeenVersion(null); // never seen any version
        userRepository.saveAndFlush(user);

        // 1. Initially should have unread notes
        assertTrue(releaseNotesService.hasUnreadNotes(user), "User with null last_seen_version should have unread notes");

        // 2. Acknowledge current version
        String currentVersion = appVersionService.getVersion();
        boolean ack = releaseNotesService.acknowledgeVersion(username, currentVersion);
        assertTrue(ack, "Acknowledgement should succeed");

        // 3. User should now NOT have unread notes
        AppUser refreshed = userRepository.findFirstByUsername(username).orElseThrow();
        assertEquals(currentVersion, refreshed.getLastSeenVersion());
        assertFalse(releaseNotesService.hasUnreadNotes(refreshed), "User who acknowledged current version should have no unread notes");

        // 4. If user saw an older version, hasUnreadNotes should be true
        refreshed.setLastSeenVersion("1.0.0");
        assertTrue(releaseNotesService.hasUnreadNotes(refreshed), "User with older last_seen_version should have unread notes");
    }

    @Test
    @DisplayName("Maintenance banner toggle and message update works seamlessly")
    void testMaintenanceBannerToggleAndUpdate() {
        // Update banner to active
        maintenanceBannerService.updateBanner(
                true,
                "System update scheduled for 21:00 WIB",
                "Pembaruan sistem dijadwalkan pukul 21:00 WIB",
                "warning",
                "super_admin"
        );

        assertTrue(maintenanceBannerService.isBannerActive());
        assertEquals("System update scheduled for 21:00 WIB", maintenanceBannerService.getBannerMessageEn());
        assertEquals("Pembaruan sistem dijadwalkan pukul 21:00 WIB", maintenanceBannerService.getBannerMessageId());
        assertEquals("warning", maintenanceBannerService.getBannerType());

        // Deactivate banner
        maintenanceBannerService.updateBanner(
                false,
                "System update scheduled for 21:00 WIB",
                "Pembaruan sistem dijadwalkan pukul 21:00 WIB",
                "info",
                "super_admin"
        );

        assertFalse(maintenanceBannerService.isBannerActive());
    }

    @Test
    @DisplayName("AppVersionService formats version and build date cleanly")
    void testAppVersionService() {
        assertNotNull(appVersionService.getVersion());
        assertNotNull(appVersionService.getVersionFormatted());
        assertTrue(appVersionService.getVersionFormatted().startsWith("v1.5.0"),
                "Formatted version should start with 'v1.5.0'");
    }

    @Test
    @DisplayName("POM version parsed directly from pom.xml matches project version and release notes")
    void testPomVersionMatchesReleaseNotes() throws Exception {
        java.io.File pomFile = new java.io.File("pom.xml");
        assertTrue(pomFile.exists(), "pom.xml must exist");

        javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        org.w3c.dom.Document doc = factory.newDocumentBuilder().parse(pomFile);
        org.w3c.dom.Element root = doc.getDocumentElement();

        String pomVersion = null;
        org.w3c.dom.NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if ("version".equals(node.getLocalName())) {
                pomVersion = node.getTextContent().trim();
                break;
            }
        }

        System.out.println("Detected POM project version: " + pomVersion);
        assertEquals("1.5.0", pomVersion, "Project version in pom.xml must be 1.5.0");

        List<ReleaseNoteDto> allNotes = releaseNotesService.getAllNotes();
        final String expectedVersion = pomVersion;
        boolean foundInNotes = allNotes.stream().anyMatch(n -> n.getVersion().equalsIgnoreCase(expectedVersion));
        assertTrue(foundInNotes, "release-notes.json must contain entry for pom version: " + pomVersion);
    }
}
