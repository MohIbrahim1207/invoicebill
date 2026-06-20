/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.config.RoleBasedLogoutSuccessHandler
 *  jakarta.servlet.ServletException
 *  jakarta.servlet.http.HttpServletRequest
 *  jakarta.servlet.http.HttpServletResponse
 *  org.springframework.security.core.Authentication
 *  org.springframework.security.core.GrantedAuthority
 *  org.springframework.security.web.authentication.logout.LogoutSuccessHandler
 *  org.springframework.stereotype.Component
 */
package com.billing.invoicehub.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import com.billing.invoicehub.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class RoleBasedLogoutSuccessHandler
implements LogoutSuccessHandler {

    @Autowired
    private AuditLogService auditLogService;

    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        if (authentication != null) {
            String username = authentication.getName();
            String role = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(java.util.stream.Collectors.joining(","));
            auditLogService.log(username, role, "Logout", null, null, null, "User successfully logged out");
        }
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_ADMIN"::equals);
        String targetUrl = isAdmin ? "/admin/login?logout" : "/login?logout";
        response.sendRedirect(request.getContextPath() + targetUrl);
    }
}

