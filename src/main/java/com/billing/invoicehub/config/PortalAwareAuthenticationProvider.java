package com.billing.invoicehub.config;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;

/**
 * Wraps an underlying {@link AuthenticationProvider} and enforces that the
 * authenticated principal holds a specific role.  If the credentials are valid
 * but the account does not have the required role the authentication is
 * rejected with a meaningful message — <em>before</em> any success handler
 * executes.  This ensures complete portal isolation without duplicating
 * password-verification logic.
 */
public class PortalAwareAuthenticationProvider implements AuthenticationProvider {

    private final AuthenticationProvider delegate;
    private final String requiredRole;
    private final String portalErrorMessage;

    /**
     * @param delegate         The real provider that verifies credentials
     *                         (typically a {@code DaoAuthenticationProvider}).
     * @param requiredRole     The Spring-Security role string that must be
     *                         present, e.g. {@code "ROLE_ADMIN"}.
     * @param portalErrorMessage The human-readable message to embed in the
     *                           thrown exception so the failure handler can
     *                           redirect to an appropriate URL.
     */
    public PortalAwareAuthenticationProvider(AuthenticationProvider delegate,
                                             String requiredRole,
                                             String portalErrorMessage) {
        this.delegate = delegate;
        this.requiredRole = requiredRole;
        this.portalErrorMessage = portalErrorMessage;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        // Delegate the actual credential check — may throw BadCredentialsException,
        // DisabledException, etc. for invalid credentials.
        Authentication result = delegate.authenticate(authentication);

        // Credentials are valid; now enforce the portal role constraint.
        boolean hasRequiredRole = result.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(requiredRole::equals);

        if (!hasRequiredRole) {
            // Throw with the portal-specific message so the failure handler
            // can distinguish this case from a plain wrong-password error.
            throw new BadCredentialsException(portalErrorMessage);
        }

        return result;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }
}
