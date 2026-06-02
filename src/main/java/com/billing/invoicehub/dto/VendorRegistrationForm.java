/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.dto.VendorRegistrationForm
 */
package com.billing.invoicehub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class VendorRegistrationForm {
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username may contain only letters, numbers, dots, underscores, and hyphens")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters long")
    private String password;

    @NotBlank(message = "Please confirm your password")
    @Size(min = 8, max = 100, message = "Confirmation password must be at least 8 characters long")
    private String confirmPassword;

    @NotBlank(message = "Company name is required")
    @Size(max = 150, message = "Company name must not exceed 150 characters")
    private String companyName;

    @NotBlank(message = "Company address is required")
    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;

    @NotBlank(message = "Full name is required")
    @Size(max = 150, message = "Full name must not exceed 150 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Size(max = 30, message = "Phone number must not exceed 30 characters")
    @Pattern(regexp = "^[0-9+()\\-\\s]{7,30}$", message = "Phone number contains invalid characters")
    private String phone;

    @NotBlank(message = "GST / VAT number is required")
    @Size(max = 50, message = "GST / VAT number must not exceed 50 characters")
    private String gstNumber;

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return this.confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public String getCompanyName() {
        return this.companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getAddress() {
        return this.address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getFullName() {
        return this.fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return this.email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return this.phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getGstNumber() {
        return this.gstNumber;
    }

    public void setGstNumber(String gstNumber) {
        this.gstNumber = gstNumber;
    }
}

