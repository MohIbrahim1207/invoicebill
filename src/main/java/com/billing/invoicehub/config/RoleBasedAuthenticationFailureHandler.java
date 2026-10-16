package com.billing.invoicehub.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class RoleBasedAuthenticationFailureHandler implements AuthenticationFailureHandler {

    public static final String MSG_WRONG_PORTAL = "PORTAL_ERROR:wrong_portal";
    public static final String MSG_NON_ADMIN_ON_ADMIN_PORTAL = "PORTAL_ERROR:non_admin_portal";

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                         HttpServletResponse response,
                                         AuthenticationException exception)
            throws IOException, ServletException {

        String requestUri = request.getRequestURI();
        boolean isAdminPortal = requestUri.contains("/admin/");

        String loginPage = isAdminPortal ? "/admin/login" : "/vendor/login";

        if (exception instanceof DisabledException) {
            response.sendRedirect(request.getContextPath() + loginPage + "?pendingVerification=true");
            return;
        }

        String exceptionMessage = exception.getMessage();

        if (exceptionMessage != null && exceptionMessage.startsWith("PORTAL_ERROR:")) {
            String[] parts = exceptionMessage.split(":");
            String errorCode = parts.length > 1 ? parts[1] : "wrong_portal";
            String rawRole = parts.length > 2 ? parts[2] : "";
            String detectedRole = rawRole.replace("ROLE_", "");

            if ("non_admin_portal".equals(errorCode)) {
                errorCode = "vendor_portal";
            }

            String redirectUrl = request.getContextPath() + loginPage + "?error=" + errorCode;
            if (!detectedRole.isBlank()) {
                redirectUrl += "&detectedRole=" + detectedRole;
            }
            response.sendRedirect(redirectUrl);
            return;
        }

        response.sendRedirect(request.getContextPath() + loginPage + "?error=invalid");
    }
}
