/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.dto.VendorRegistrationForm
 *  com.billing.invoicehub.entity.AppRole
 *  com.billing.invoicehub.entity.AppUser
 *  com.billing.invoicehub.entity.NotificationType
 *  com.billing.invoicehub.repository.AppRoleRepository
 *  com.billing.invoicehub.repository.AppUserRepository
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.security.crypto.password.PasswordEncoder
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.web.multipart.MultipartFile
 */
package com.billing.invoicehub.service;

import com.billing.invoicehub.dto.VendorRegistrationForm;
import com.billing.invoicehub.entity.AppRole;
import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.NotificationType;
import com.billing.invoicehub.repository.AppRoleRepository;
import com.billing.invoicehub.repository.AppUserRepository;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VendorRegistrationService {
    private static final Logger log = LoggerFactory.getLogger(VendorRegistrationService.class);
    private static final String RESEND_FROM = "onboarding@resend.dev";
    private final AppUserRepository userRepository;
    private final AppRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final EmailService emailService;

    @org.springframework.beans.factory.annotation.Autowired
    private AuditLogService auditLogService;

    public VendorRegistrationService(AppUserRepository userRepository, AppRoleRepository roleRepository,
            PasswordEncoder passwordEncoder, FileStorageService fileStorageService,
            NotificationService notificationService, EmailService emailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
        this.emailService = emailService;
    }

    @Transactional(readOnly = true)
    public List<AppUser> listVendors() {
        return this.userRepository.findByRoles_NameOrderByIdDesc("ROLE_VENDOR").stream()
                .filter(arg_0 -> this.looksLikeVendorRegistration(arg_0)).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> getVendor(Long id) {
        return this.userRepository.findById(id)
                .filter(user -> user.getRoles() != null && user.getRoles().stream()
                        .anyMatch(role -> role.getName() != null && role.getName().equals("ROLE_VENDOR")))
                .filter(arg_0 -> this.looksLikeVendorRegistration(arg_0));
    }

    @Transactional
    public AppUser registerVendor(VendorRegistrationForm form, MultipartFile gstDocument, MultipartFile companyDocument,
            MultipartFile supportingDocument) throws IOException {
        log.info("[DEBUG-LOG-SIGNUP] VendorRegistrationService.registerVendor() executed. Username: {}", form.getUsername());
        log.info("=== Starting vendor registration for username: {} ===", form.getUsername());

        long maxSizeBytes = 10 * 1024 * 1024;
        String sizeError = "File size exceeds the maximum allowed limit of 10 MB. Please upload a smaller file.";
        if ((gstDocument != null && gstDocument.getSize() > maxSizeBytes) ||
                (companyDocument != null && companyDocument.getSize() > maxSizeBytes) ||
                (supportingDocument != null && supportingDocument.getSize() > maxSizeBytes)) {
            throw new IllegalArgumentException(sizeError);
        }

        this.validateRegistration(form, gstDocument, companyDocument);
        if (this.userRepository.findByUsername(form.getUsername().trim()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (this.userRepository.findByEmailIgnoreCase(form.getEmail().trim()).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }
        if (this.userRepository.findByCompanyNameIgnoreCase(form.getCompanyName().trim()).isPresent()) {
            throw new IllegalArgumentException("Company Name already exists");
        }
        if (this.userRepository.findByGstNumber(form.getGstNumber().trim()).isPresent()) {
            throw new IllegalArgumentException("GST Number already exists");
        }
        AppRole userRole = (AppRole) this.roleRepository.findByName("ROLE_VENDOR")
                .orElseThrow(() -> new IllegalStateException("ROLE_VENDOR not found"));
        AppUser user = new AppUser();
        user.setUsername(form.getUsername().trim());
        user.setPassword(this.passwordEncoder.encode((CharSequence) form.getPassword()));
        user.setEmail(form.getEmail().trim());
        user.setCompanyName(form.getCompanyName().trim());
        user.setAddress(form.getAddress().trim());
        user.setFullName(form.getFullName().trim());
        user.setPhone(form.getPhone().trim());
        user.setGstNumber(form.getGstNumber().trim());
        user.setVerified(false);
        user.setEnabled(true);
        user.setVendorCode(null);
        user.setRejectionReason(null);
        user.setRegistrationDate(LocalDateTime.now());

        log.debug("Uploading GST document for user: {}", form.getUsername());
        try {
            String gstUrl = this.fileStorageService.storeVendorDocument(gstDocument);
            log.debug("GST document uploaded successfully. URL: {}", gstUrl);
            user.setGstDocumentUrl(gstUrl);
        } catch (Exception ex) {
            log.error("Failed to upload GST document for user {}: {}", form.getUsername(), ex.getMessage(), ex);
            throw new IOException("Failed to upload GST document: " + ex.getMessage(), ex);
        }

        log.debug("Uploading company document for user: {}", form.getUsername());
        try {
            String companyUrl = this.fileStorageService.storeVendorDocument(companyDocument);
            log.debug("Company document uploaded successfully. URL: {}", companyUrl);
            user.setCompanyDocumentUrl(companyUrl);
        } catch (Exception ex) {
            log.error("Failed to upload company document for user {}: {}", form.getUsername(), ex.getMessage(), ex);
            throw new IOException("Failed to upload company document: " + ex.getMessage(), ex);
        }

        if (supportingDocument != null && !supportingDocument.isEmpty()) {
            log.debug("Uploading supporting document for user: {}", form.getUsername());
            try {
                String supportingUrl = this.fileStorageService.storeVendorDocument(supportingDocument);
                log.debug("Supporting document uploaded successfully. URL: {}", supportingUrl);
                user.setSupportingDocumentUrl(supportingUrl);
            } catch (Exception ex) {
                log.error("Failed to upload supporting document for user {}: {}", form.getUsername(), ex.getMessage(),
                        ex);
                throw new IOException("Failed to upload supporting document: " + ex.getMessage(), ex);
            }
        }

        user.setRoles(Set.of(userRole));
        log.debug("Saving user to database: {}", form.getUsername());
        log.info("[DEBUG-LOG-SIGNUP] appUserRepository.save() called. Username: {}", user.getUsername());
        AppUser saved = this.userRepository.save(user);
        auditLogService.log(saved.getUsername(), "ROLE_VENDOR", "Vendor Registration", "AppUser", saved.getId(), null,
                "Vendor registered: " + saved.getUsername());
        try {
            notificationService.createNotification(
                    "New Vendor Registration",
                    String.format("Vendor \"%s\" has registered and is awaiting verification.", saved.getCompanyName()),
                    NotificationType.VENDOR_REGISTRATION,
                    "ROLE_ADMIN",
                    saved.getId(),
                    "VENDOR");
            log.info("Created and broadcasted new vendor registration notification for {}", saved.getUsername());
        } catch (Exception ex) {
            log.error("Failed to create vendor registration notification: {}", ex.getMessage(), ex);
        }
        this.sendRegistrationEmail(saved);
        log.info("Vendor registration received for {}", (Object) saved.getUsername());
        log.info("=== Vendor registration completed successfully for: {} ===", saved.getUsername());
        return saved;
    }

    @Transactional
    public AppUser verifyVendor(Long vendorId) {
        AppUser vendor = (AppUser) this.getVendor(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found"));
        if (vendor.isVerified() && vendor.getVendorCode() != null && !vendor.getVendorCode().isBlank()) {
            return vendor;
        }
        vendor.setVerified(true);
        vendor.setEnabled(true);
        vendor.setRejectionReason(null);
        vendor.setVendorCode(this.generateVendorCode());
        AppUser saved = this.userRepository.save(vendor);
        auditLogService.log("Approval", "AppUser", saved.getId(), "Pending", "Verified: " + saved.getVendorCode());
        this.sendVerificationEmail(saved);
        log.info("Verified vendor {} with code {}", (Object) saved.getUsername(), (Object) saved.getVendorCode());
        try {
            String notifTitle = "Account Verified";
            String notifMessage = String.format("Hello %s, your account has been verified. Vendor code: %s",
                    this.nullSafe(saved.getFullName()), this.nullSafe(saved.getVendorCode()));
            this.notificationService.createNotification(notifTitle, notifMessage, NotificationType.VENDOR_APPROVED,
                    saved);
        } catch (Exception ex) {
            log.error("Failed to create in-app notification for vendor {}: {}",
                    new Object[] { saved.getUsername(), ex.getMessage(), ex });
        }
        return saved;
    }

    @Transactional
    public AppUser rejectVendor(Long vendorId, String reason) {
        String trimmedReason;
        AppUser vendor = (AppUser) this.getVendor(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found"));
        String string = trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        vendor.setVerified(false);
        vendor.setEnabled(true);
        vendor.setVendorCode(null);
        vendor.setRejectionReason(trimmedReason);
        AppUser saved = this.userRepository.save(vendor);
        auditLogService.log("Rejection", "AppUser", saved.getId(), "Pending", "Rejected: " + trimmedReason);
        this.sendRejectionEmail(saved, trimmedReason);
        log.info("Rejected vendor {}", (Object) saved.getUsername());
        return saved;
    }

    @Transactional(readOnly = true)
    public String getVendorStatus(AppUser vendor) {
        if (vendor == null) {
            return "Pending";
        }
        if (vendor.isVerified()) {
            return "Verified";
        }
        if (vendor.getRejectionReason() != null && !vendor.getRejectionReason().isBlank()) {
            return "Rejected";
        }
        return "Pending";
    }

    private void validateRegistration(VendorRegistrationForm form, MultipartFile gstDocument,
            MultipartFile companyDocument) {
        if (form == null) {
            throw new IllegalArgumentException("Registration form is required");
        }
        if (this.isBlank(form.getUsername()) || this.isBlank(form.getPassword())
                || this.isBlank(form.getConfirmPassword()) || this.isBlank(form.getCompanyName())
                || this.isBlank(form.getAddress()) || this.isBlank(form.getFullName()) || this.isBlank(form.getEmail())
                || this.isBlank(form.getPhone()) || this.isBlank(form.getGstNumber())) {
            throw new IllegalArgumentException("Please fill all required fields");
        }
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        if (form.getPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        if (gstDocument == null || gstDocument.isEmpty()) {
            throw new IllegalArgumentException("GST document is required");
        }
        if (companyDocument == null || companyDocument.isEmpty()) {
            throw new IllegalArgumentException("Company registration document is required");
        }
    }

    private String generateVendorCode() {
        int year = Year.now().getValue();
        String prefix = String.format("VC-%d-", year);
        long nextNumber = this.userRepository.countByVendorCodeStartingWith(prefix) + 1L;
        String candidate = String.format("VC-%d-%05d", year, nextNumber);
        while (this.userRepository.findByVendorCode(candidate).isPresent()) {
            candidate = String.format("VC-%d-%05d", year, ++nextNumber);
        }
        return candidate;
    }

    private void sendRegistrationEmail(AppUser vendor) {
        this.emailService.sendRegistrationReceivedEmail(vendor.getEmail(), this.nullSafe(vendor.getFullName()),
                this.nullSafe(vendor.getCompanyName()));
    }

    private void sendVerificationEmail(AppUser vendor) {
        this.emailService.sendVendorApprovalEmail(vendor.getEmail(), this.nullSafe(vendor.getFullName()),
                this.nullSafe(vendor.getVendorCode()));
    }

    private void sendRejectionEmail(AppUser vendor, String reason) {
        this.emailService.sendVendorRejectionEmail(vendor.getEmail(), this.nullSafe(vendor.getFullName()), reason);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean looksLikeVendorRegistration(AppUser user) {
        return user != null && (user.getRegistrationDate() != null || !this.isBlank(user.getCompanyName())
                || !this.isBlank(user.getGstNumber()) || !this.isBlank(user.getVendorCode())
                || !this.isBlank(user.getGstDocumentUrl()) || !this.isBlank(user.getCompanyDocumentUrl())
                || !this.isBlank(user.getSupportingDocumentUrl()) || !this.isBlank(user.getRejectionReason()));
    }

    private String nullSafe(String value) {
        return value == null ? "-" : value;
    }

    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String username) {
        if (username == null || username.trim().isEmpty()) return false;
        return this.userRepository.findByUsername(username.trim()).isEmpty();
    }

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        if (email == null || email.trim().isEmpty()) return false;
        return this.userRepository.findByEmailIgnoreCase(email.trim()).isEmpty();
    }

    @Transactional(readOnly = true)
    public boolean isGstAvailable(String gstNumber) {
        if (gstNumber == null || gstNumber.trim().isEmpty()) return false;
        return this.userRepository.findByGstNumber(gstNumber.trim()).isEmpty();
    }
}
