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
 *  com.billing.invoicehub.service.FileStorageService
 *  com.billing.invoicehub.service.NotificationService
 *  com.billing.invoicehub.service.VendorRegistrationService
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.annotation.Value
 *  org.springframework.mail.SimpleMailMessage
 *  org.springframework.mail.javamail.JavaMailSender
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
import com.billing.invoicehub.service.FileStorageService;
import com.billing.invoicehub.service.NotificationService;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VendorRegistrationService {
    private static final Logger log = LoggerFactory.getLogger(VendorRegistrationService.class);
    private final AppUserRepository userRepository;
    private final AppRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String adminEmail;
    private final NotificationService notificationService;

    public VendorRegistrationService(AppUserRepository userRepository, AppRoleRepository roleRepository, PasswordEncoder passwordEncoder, FileStorageService fileStorageService, JavaMailSender mailSender, NotificationService notificationService, @Value(value="${spring.mail.username:}") String mailFrom, @Value(value="${app.admin.email:}") String adminEmail) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.adminEmail = adminEmail;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly=true)
    public List<AppUser> listVendors() {
        return this.userRepository.findByRoles_NameOrderByIdDesc("ROLE_USER").stream().filter(arg_0 -> this.looksLikeVendorRegistration(arg_0)).collect(Collectors.toList());
    }

    @Transactional(readOnly=true)
    public Optional<AppUser> getVendor(Long id) {
        return this.userRepository.findById(id).filter(user -> user.getRoles() != null && user.getRoles().stream().anyMatch(role -> role.getName() != null && role.getName().equals("ROLE_USER"))).filter(arg_0 -> this.looksLikeVendorRegistration(arg_0));
    }

    @Transactional
    public AppUser registerVendor(VendorRegistrationForm form, MultipartFile gstDocument, MultipartFile companyDocument, MultipartFile supportingDocument) throws IOException {
        this.validateRegistration(form, gstDocument, companyDocument);
        if (this.userRepository.findByUsername(form.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        AppRole userRole = (AppRole)this.roleRepository.findByName("ROLE_USER").orElseThrow(() -> new IllegalStateException("ROLE_USER not found"));
        AppUser user = new AppUser();
        user.setUsername(form.getUsername().trim());
        user.setPassword(this.passwordEncoder.encode((CharSequence)form.getPassword()));
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
        user.setGstDocumentPath(this.fileStorageService.storeVendorDocument(gstDocument));
        user.setCompanyDocumentPath(this.fileStorageService.storeVendorDocument(companyDocument));
        if (supportingDocument != null && !supportingDocument.isEmpty()) {
            user.setSupportingDocumentPath(this.fileStorageService.storeVendorDocument(supportingDocument));
        }
        user.setRoles(Set.of(userRole));
        AppUser saved = this.userRepository.save(user);
        this.sendRegistrationEmail(saved);
        log.info("Vendor registration received for {}", (Object)saved.getUsername());
        return saved;
    }

    @Transactional
    public AppUser verifyVendor(Long vendorId) {
        AppUser vendor = (AppUser)this.getVendor(vendorId).orElseThrow(() -> new IllegalArgumentException("Vendor not found"));
        if (vendor.isVerified() && vendor.getVendorCode() != null && !vendor.getVendorCode().isBlank()) {
            return vendor;
        }
        vendor.setVerified(true);
        vendor.setEnabled(true);
        vendor.setRejectionReason(null);
        vendor.setVendorCode(this.generateVendorCode());
        AppUser saved = this.userRepository.save(vendor);
        this.sendVerificationEmail(saved);
        log.info("Verified vendor {} with code {}", (Object)saved.getUsername(), (Object)saved.getVendorCode());
        try {
            String notifTitle = "Account Verified";
            String notifMessage = String.format("Hello %s, your account has been verified. Vendor code: %s", this.nullSafe(saved.getFullName()), this.nullSafe(saved.getVendorCode()));
            this.notificationService.createNotification(notifTitle, notifMessage, NotificationType.VENDOR_APPROVED, saved);
        }
        catch (Exception ex) {
            log.error("Failed to create in-app notification for vendor {}: {}", new Object[]{saved.getUsername(), ex.getMessage(), ex});
        }
        return saved;
    }

    @Transactional
    public AppUser rejectVendor(Long vendorId, String reason) {
        String trimmedReason;
        AppUser vendor = (AppUser)this.getVendor(vendorId).orElseThrow(() -> new IllegalArgumentException("Vendor not found"));
        String string = trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        vendor.setVerified(false);
        vendor.setEnabled(true);
        vendor.setVendorCode(null);
        vendor.setRejectionReason(trimmedReason);
        AppUser saved = this.userRepository.save(vendor);
        this.sendRejectionEmail(saved, trimmedReason);
        log.info("Rejected vendor {}", (Object)saved.getUsername());
        return saved;
    }

    @Transactional(readOnly=true)
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

    private void validateRegistration(VendorRegistrationForm form, MultipartFile gstDocument, MultipartFile companyDocument) {
        if (form == null) {
            throw new IllegalArgumentException("Registration form is required");
        }
        if (this.isBlank(form.getUsername()) || this.isBlank(form.getPassword()) || this.isBlank(form.getConfirmPassword()) || this.isBlank(form.getCompanyName()) || this.isBlank(form.getAddress()) || this.isBlank(form.getFullName()) || this.isBlank(form.getEmail()) || this.isBlank(form.getPhone()) || this.isBlank(form.getGstNumber())) {
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
        this.sendMail(vendor.getEmail(), "InvoiceHub - Registration Received", String.format("Your registration is under review.%n%nCompany: %s%n%nYou will be notified once verified.", this.nullSafe(vendor.getCompanyName())));
    }

    private void sendVerificationEmail(AppUser vendor) {
        this.sendMail(vendor.getEmail(), "InvoiceHub - Account Verified", String.format("Welcome! Your account has been verified.%n%nYour Vendor Code: %s%n%nYou can now login and submit invoices.", this.nullSafe(vendor.getVendorCode())));
    }

    private void sendRejectionEmail(AppUser vendor, String reason) {
        this.sendMail(vendor.getEmail(), "InvoiceHub - Registration Rejected", String.format("Your registration was rejected:%n%n%s", reason));
    }

    private void sendMail(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            log.warn("Skipping email '{}' because recipient address is missing", (Object)subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            if (this.mailFrom != null && !this.mailFrom.isBlank()) {
                message.setFrom(this.mailFrom);
            }
            this.mailSender.send(message);
        }
        catch (Exception ex) {
            log.error("Failed to send email '{}' to {}: {}", new Object[]{subject, to, ex.getMessage(), ex});
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean looksLikeVendorRegistration(AppUser user) {
        return user != null && (user.getRegistrationDate() != null || !this.isBlank(user.getCompanyName()) || !this.isBlank(user.getGstNumber()) || !this.isBlank(user.getVendorCode()) || !this.isBlank(user.getGstDocumentPath()) || !this.isBlank(user.getCompanyDocumentPath()) || !this.isBlank(user.getSupportingDocumentPath()) || !this.isBlank(user.getRejectionReason()));
    }

    private String nullSafe(String value) {
        return value == null ? "-" : value;
    }
}

