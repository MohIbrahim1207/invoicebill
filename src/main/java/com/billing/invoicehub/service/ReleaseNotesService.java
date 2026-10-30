package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.ReleaseNoteDto;
import com.billing.invoicehub.entity.AppRole;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AppUserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReleaseNotesService {
    private static final Logger log = LoggerFactory.getLogger(ReleaseNotesService.class);

    private final AppUserRepository userRepository;
    private final ObjectMapper objectMapper;
    private List<ReleaseNoteDto> cachedReleaseNotes = new ArrayList<>();

    public ReleaseNotesService(AppUserRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public synchronized void reloadReleaseNotes() {
        try {
            ClassPathResource resource = new ClassPathResource("release-notes.json");
            if (!resource.exists()) {
                log.warn("release-notes.json not found on classpath!");
                this.cachedReleaseNotes = Collections.emptyList();
                return;
            }

            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                List<ReleaseNoteDto> list = new ArrayList<>();
                if (root.isArray()) {
                    for (JsonNode node : root) {
                        ReleaseNoteDto dto = new ReleaseNoteDto();
                        dto.setVersion(node.path("version").asText(""));
                        dto.setDate(node.path("date").asText(""));
                        dto.setAudience(node.path("audience").asText("ALL").toUpperCase());

                        JsonNode titleNode = node.path("title");
                        if (titleNode.isObject()) {
                            dto.setTitleEn(titleNode.path("en").asText(""));
                            dto.setTitleId(titleNode.path("id").asText(""));
                        } else if (titleNode.isTextual()) {
                            dto.setTitleEn(titleNode.asText(""));
                            dto.setTitleId(titleNode.asText(""));
                        }

                        JsonNode notesNode = node.path("notes");
                        if (notesNode.isObject()) {
                            List<String> en = new ArrayList<>();
                            List<String> id = new ArrayList<>();
                            notesNode.path("en").forEach(n -> en.add(n.asText()));
                            notesNode.path("id").forEach(n -> id.add(n.asText()));
                            dto.setNotesEn(en);
                            dto.setNotesId(id);
                        }
                        list.add(dto);
                    }
                }
                this.cachedReleaseNotes = Collections.unmodifiableList(list);
                log.info("Loaded {} release notes entries from release-notes.json", list.size());
            }
        } catch (Exception ex) {
            log.error("Failed to parse release-notes.json: {}", ex.getMessage(), ex);
            this.cachedReleaseNotes = Collections.emptyList();
        }
    }

    public List<ReleaseNoteDto> getAllNotes() {
        return cachedReleaseNotes;
    }

    public List<ReleaseNoteDto> getNotesForAudience(String audience) {
        if (audience == null || audience.isBlank() || "ALL".equalsIgnoreCase(audience)) {
            return cachedReleaseNotes;
        }
        return cachedReleaseNotes.stream()
                .filter(n -> "ALL".equalsIgnoreCase(n.getAudience()) || audience.equalsIgnoreCase(n.getAudience()))
                .collect(Collectors.toList());
    }

    public List<ReleaseNoteDto> getNotesForUser(AppUser user) {
        String audience = resolveAudience(user);
        return getNotesForAudience(audience);
    }

    public Optional<ReleaseNoteDto> getLatestNoteForUser(AppUser user) {
        List<ReleaseNoteDto> notes = getNotesForUser(user);
        if (notes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(notes.get(0));
    }

    public boolean hasUnreadNotes(AppUser user) {
        if (user == null) {
            return false;
        }
        Optional<ReleaseNoteDto> latestOpt = getLatestNoteForUser(user);
        if (latestOpt.isEmpty()) {
            return false;
        }
        ReleaseNoteDto latest = latestOpt.get();
        String lastSeen = user.getLastSeenVersion();
        if (lastSeen == null || lastSeen.isBlank()) {
            return true;
        }
        return !lastSeen.trim().equalsIgnoreCase(latest.getVersion().trim());
    }

    @Transactional
    public boolean acknowledgeVersion(String username, String version) {
        if (username == null || version == null) {
            return false;
        }
        Optional<AppUser> userOpt = userRepository.findFirstByUsername(username);
        if (userOpt.isPresent()) {
            AppUser user = userOpt.get();
            user.setLastSeenVersion(version.trim());
            userRepository.save(user);
            log.info("User {} acknowledged release notes for version {}", username, version);
            return true;
        }
        return false;
    }

    public String resolveAudience(AppUser user) {
        if (user == null || user.getRoles() == null) {
            return "ALL";
        }
        boolean isVendor = false;
        boolean isAdmin = false;
        for (AppRole r : user.getRoles()) {
            if (r.getName() != null) {
                if (r.getName().equals("ROLE_VENDOR")) {
                    isVendor = true;
                } else if (r.getName().contains("ADMIN") || r.getName().contains("ACCOUNTS") || r.getName().contains("EMPLOYEE") || r.getName().contains("MANAGER")) {
                    isAdmin = true;
                }
            }
        }
        if (isAdmin) {
            return "ADMIN";
        }
        if (isVendor) {
            return "VENDOR";
        }
        return "ALL";
    }
}
