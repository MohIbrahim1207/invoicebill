package com.billing.invoicehub.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String password;
    private String email;
    private boolean enabled;
    private boolean verified;
    private String vendorCode;
    private String rejectionReason;
    private LocalDateTime registrationDate;
    private String companyName;
    private String address;
    private String fullName;
    private String phone;
    private String gstNumber;
    private String gstDocumentPath;
    private String companyDocumentPath;
    private String supportingDocumentPath;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "app_user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<AppRole> roles = new HashSet<>();

    public Long getId() { return this.id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return this.username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return this.password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return this.email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isEnabled() { return this.enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isVerified() { return this.verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public String getVendorCode() { return this.vendorCode; }
    public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }

    public String getRejectionReason() { return this.rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public LocalDateTime getRegistrationDate() { return this.registrationDate; }
    public void setRegistrationDate(LocalDateTime registrationDate) { this.registrationDate = registrationDate; }

    public String getCompanyName() { return this.companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getAddress() { return this.address; }
    public void setAddress(String address) { this.address = address; }

    public String getFullName() { return this.fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return this.phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getGstNumber() { return this.gstNumber; }
    public void setGstNumber(String gstNumber) { this.gstNumber = gstNumber; }

    public String getGstDocumentPath() { return this.gstDocumentPath; }
    public void setGstDocumentPath(String gstDocumentPath) { this.gstDocumentPath = gstDocumentPath; }

    public String getCompanyDocumentPath() { return this.companyDocumentPath; }
    public void setCompanyDocumentPath(String companyDocumentPath) { this.companyDocumentPath = companyDocumentPath; }

    public String getSupportingDocumentPath() { return this.supportingDocumentPath; }
    public void setSupportingDocumentPath(String supportingDocumentPath) { this.supportingDocumentPath = supportingDocumentPath; }

    public Set<AppRole> getRoles() { return this.roles; }
    public void setRoles(Set<AppRole> roles) { this.roles = roles; }
}


