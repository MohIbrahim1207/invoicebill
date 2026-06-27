package com.billing.invoicehub.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Centralized failure handler for both the Vendor and Admin login portals.
 *
 * <p>The portal-role enforcement is done entirely by {@link PortalAwareAuthenticationProvider},
 * which embeds a specific message in the thrown {@link org.springframework.security.authentication.BadCredentialsException}
 * when a user attempts to authenticate via the wrong portal.  This handler
 * inspects that message to choose the correct redirect URL and error parameter.</p>
 *
 * <p>Error parameter convention:
 * <ul>
 *   <li>{@code ?error=admin_portal}  – admin tried to log in via the Vendor portal</li>
 *   <li>{@code ?error=vendor_portal} – vendor tried to log in via the Admin portal</li>
 *   <li>{@code ?error=invalid}       – generic bad credentials / disabled</li>
 *   <li>{@code ?pendingVerification=true} – account awaiting approval</li>
 * </ul>
 * </p>
 */
@Component
public class RoleBasedAuthenticationFailureHandler implements AuthenticationFailureHandler {

    /** Injected by {@link PortalAwareAuthenticationProvider} on role mismatch — admin on vendor portal. */
    public static final String MSG_ADMIN_ON_VENDOR_PORTAL = "PORTAL_ERROR:admin_portal";

    /** Injected by {@link PortalAwareAuthenticationProvider} on role mismatch — vendor on admin portal. */
    public static final String MSG_VENDOR_ON_ADMIN_PORTAL = "PORTAL_ERROR:vendor_portal";

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception)
            throws IOException, ServletException {

        String requestUri = request.getRequestURI();
        boolean isAdminPortal = requestUri.contains("/admin/");

        // Account pending admin verification
        if (exception instanceof DisabledException) {
            String pendingUrl = isAdminPortal
                    ? "/admin/login?pendingVerification=true"
                    : "/login?pendingVerification=true";
            response.sendRedirect(request.getContextPath() + pendingUrl);
            return;
        }

        String exceptionMessage = exception.getMessage();

        // Portal-specific role-mismatch errors set by PortalAwareAuthenticationProvider
        if (MSG_ADMIN_ON_VENDOR_PORTAL.equals(exceptionMessage)) {
            // Admin tried the Vendor portal
            response.sendRedirect(request.getContextPath() + "/login?error=admin_portal");
            return;
        }

        if (MSG_VENDOR_ON_ADMIN_PORTAL.equals(exceptionMessage)) {
            // Vendor tried the Admin portal
            response.sendRedirect(request.getContextPath() + "/admin/login?error=vendor_portal");
            return;
        }

        // Generic failure — wrong password, unknown user, etc.
        String genericFailUrl = isAdminPortal ? "/admin/login?error=invalid" : "/login?error=invalid";
        response.sendRedirect(request.getContextPath() + genericFailUrl);
    }
}
