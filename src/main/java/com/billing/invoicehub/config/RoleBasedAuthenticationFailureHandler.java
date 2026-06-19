/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.config.RoleBasedAuthenticationFailureHandler
 *  com.billing.invoicehub.config.RoleBasedAuthenticationValidator
 *  jakarta.servlet.ServletException
 *  jakarta.servlet.ServletRequest
 *  jakarta.servlet.ServletResponse
 *  jakarta.servlet.http.HttpServletRequest
 *  jakarta.servlet.http.HttpServletResponse
 *  org.springframework.security.authentication.DisabledException
 *  org.springframework.security.core.AuthenticationException
 *  org.springframework.security.web.authentication.AuthenticationFailureHandler
 *  org.springframework.stereotype.Component
 */
package com.billing.invoicehub.config;

import com.billing.invoicehub.config.RoleBasedAuthenticationValidator;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class RoleBasedAuthenticationFailureHandler
implements AuthenticationFailureHandler {
    private final RoleBasedAuthenticationValidator roleValidator;

    public RoleBasedAuthenticationFailureHandler(RoleBasedAuthenticationValidator roleValidator) {
        this.roleValidator = roleValidator;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        String requestPath = request.getRequestURI();
        String username = request.getParameter("username");
        String loginUrl = requestPath.contains("/admin/") ? "/admin/login" : "/login";
        if (exception instanceof DisabledException) {
            response.sendRedirect(request.getContextPath() + loginUrl + "?pendingVerification=true");
            return;
        }
        if (requestPath.contains("/admin/login") && username != null && this.roleValidator.isRegularUser(username)) {
            request.setAttribute("error", (Object)"Invalid credentials");
            request.getRequestDispatcher("/admin/login?error=invalid").forward((ServletRequest)request, (ServletResponse)response);
            return;
        }
        if (requestPath.contains("/login") && !requestPath.contains("/admin/") && username != null && this.roleValidator.isAdminUser(username)) {
            request.setAttribute("error", (Object)"Invalid credentials");
            request.getRequestDispatcher("/login?error=invalid").forward((ServletRequest)request, (ServletResponse)response);
            return;
        }
        response.sendRedirect(request.getContextPath() + loginUrl + "?error=invalid");
    }
}

