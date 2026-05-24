/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.config.RoleBasedAuthenticationValidator
 *  com.billing.invoicehub.service.CustomUserDetailsService
 *  org.springframework.security.core.GrantedAuthority
 *  org.springframework.security.core.userdetails.UserDetails
 *  org.springframework.stereotype.Component
 */
package com.billing.invoicehub.config;

import com.billing.invoicehub.service.CustomUserDetailsService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class RoleBasedAuthenticationValidator {
    private final CustomUserDetailsService userDetailsService;

    public RoleBasedAuthenticationValidator(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    public boolean isAdminUser(String username) {
        try {
            UserDetails user = this.userDetailsService.loadUserByUsername(username);
            return user.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_ADMIN"::equals);
        }
        catch (Exception e) {
            return false;
        }
    }

    public boolean isRegularUser(String username) {
        try {
            UserDetails user = this.userDetailsService.loadUserByUsername(username);
            return user.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_USER"::equals);
        }
        catch (Exception e) {
            return false;
        }
    }
}

