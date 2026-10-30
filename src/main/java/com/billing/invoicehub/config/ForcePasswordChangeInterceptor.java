package com.billing.invoicehub.config;

import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ForcePasswordChangeInterceptor implements HandlerInterceptor {

    private final AppUserRepository userRepository;

    public ForcePasswordChangeInterceptor(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return true;
        }

        String path = request.getRequestURI();
        if (isExemptPath(path)) {
            return true;
        }

        Boolean forcedInSession = (Boolean) request.getSession().getAttribute("FORCE_PASSWORD_CHANGE");
        if (forcedInSession == null) {
            // Lazy check in database
            AppUser user = userRepository.findFirstByUsername(auth.getName())
                    .orElseGet(() -> userRepository.findFirstByEmailIgnoreCase(auth.getName()).orElse(null));
            if (user != null && user.isForcePasswordChange()) {
                forcedInSession = true;
                request.getSession().setAttribute("FORCE_PASSWORD_CHANGE", true);
            } else {
                forcedInSession = false;
                request.getSession().setAttribute("FORCE_PASSWORD_CHANGE", false);
            }
        }

        if (Boolean.TRUE.equals(forcedInSession)) {
            boolean isAdmin = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                    .anyMatch(r -> r.equals("ROLE_ADMIN") || r.equals("ROLE_SUPER_ADMIN") || r.equals("ROLE_ACCOUNTS"));
            String targetRedirect = isAdmin ? "/admin/change-password" : "/change-password";
            response.sendRedirect(request.getContextPath() + targetRedirect);
            return false;
        }

        return true;
    }

    private boolean isExemptPath(String path) {
        if (path == null) return false;
        return path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.startsWith("/webjars/")
                || path.equals("/favicon.ico")
                || path.equals("/logout")
                || path.equals("/change-password")
                || path.equals("/admin/change-password");
    }
}

