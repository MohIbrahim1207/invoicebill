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
    @NotBlank(message = "{validation.username.required}")
    @Size(min = 3, max = 50, message = "{validation.username.size}")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "{validation.username.pattern}")
    private String username;

    @NotBlank(message = "{validation.password.required}")
    @Size(min = 8, max = 100, message = "{validation.password.size}")
    private String password;

    @NotBlank(message = "{validation.confirmPassword.required}")
    @Size(min = 8, max = 100, message = "{validation.confirmPassword.size}")
    private String confirmPassword;

    @NotBlank(message = "{validation.companyName.required}")
    @Size(max = 150, message = "{validation.companyName.size}")
    private String companyName;

    @NotBlank(message = "{validation.address.required}")
    @Size(max = 500, message = "{validation.address.size}")
    private String address;

    @NotBlank(message = "{validation.fullName.required}")
    @Size(max = 150, message = "{validation.fullName.size}")
    private String fullName;

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.valid}")
    @Size(max = 150, message = "{validation.email.size}")
    private String email;

    @NotBlank(message = "{validation.phone.required}")
    @Size(max = 30, message = "{validation.phone.size}")
    @Pattern(regexp = "^[0-9+()\\-\\s]{7,30}$", message = "{validation.phone.pattern}")
    private String phone;

    @NotBlank(message = "{validation.gstNumber.required}")
    @Size(max = 50, message = "{validation.gstNumber.size}")
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

