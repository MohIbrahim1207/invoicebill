package com.billing.invoicehub.config;

import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.service.AuditLogService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.stream.Collectors;

@Component
public class RoleBasedAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AppUserRepository userRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        String username = authentication != null ? authentication.getName() : null;
        if (authentication != null) {
            String role = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.joining(","));
            auditLogService.log(username, role, "Login", null, null, null, "User successfully logged in");
        }

        boolean isSuperAdmin = authentication != null && authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_SUPER_ADMIN"::equals);
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_ADMIN"::equals);
        boolean isAccounts = authentication != null && authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_ACCOUNTS"::equals);
        boolean isInternalAdmin = isSuperAdmin || isAdmin || isAccounts;

        // Check if user is required to change password on next login
        if (username != null && userRepository != null) {
            AppUser user = userRepository.findFirstByUsername(username)
                    .orElseGet(() -> userRepository.findFirstByEmailIgnoreCase(username).orElse(null));
            if (user != null && user.isForcePasswordChange()) {
                request.getSession().setAttribute("FORCE_PASSWORD_CHANGE", true);
                String changeUrl = isInternalAdmin ? "/admin/change-password" : "/change-password";
                response.sendRedirect(request.getContextPath() + changeUrl);
                return;
            }
        }

        String targetUrl = isInternalAdmin ? "/admin/dashboard" : "/invoice";
        response.sendRedirect(request.getContextPath() + targetUrl);
    }
}
