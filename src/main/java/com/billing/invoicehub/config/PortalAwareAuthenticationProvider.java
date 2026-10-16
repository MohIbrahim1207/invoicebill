package com.billing.invoicehub.config;

import com.billing.invoicehub.entity.PortalType;
import com.billing.invoicehub.service.CustomUserDetailsService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

/**
 * Custom {@link AuthenticationProvider} enforcing domain-scoped authentication and portal-role security.
 */
public class PortalAwareAuthenticationProvider implements AuthenticationProvider {

    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final PortalType portalType;
    private final String targetDomain; // "VENDOR" or "INTERNAL"
    private final Set<String> allowedRoles;
    private final String portalErrorMessage;

    public PortalAwareAuthenticationProvider(CustomUserDetailsService userDetailsService,
                                             PasswordEncoder passwordEncoder,
                                             PortalType portalType,
                                             String targetDomain,
                                             Set<String> allowedRoles,
                                             String portalErrorMessage) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.portalType = portalType;
        this.targetDomain = targetDomain;
        this.allowedRoles = allowedRoles;
        this.portalErrorMessage = portalErrorMessage;
    }

    public PortalType getPortalType() {
        return portalType;
    }

    public String getTargetDomain() {
        return targetDomain;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String rawPassword = (String) authentication.getCredentials();

        if (username == null || username.isBlank() || rawPassword == null) {
            throw new BadCredentialsException("Invalid username or password");
        }

        // 1. Look up user strictly within the target domain ("VENDOR" vs "INTERNAL")
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsernameAndDomain(username, targetDomain);
        } catch (UsernameNotFoundException ex) {
            // User does not exist in this authentication domain
            throw new BadCredentialsException("Invalid username or password");
        }

        // 2. Validate password
        if (!passwordEncoder.matches(rawPassword, userDetails.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        // 3. Validate role allowed for this specific portal
        boolean hasAllowedRole = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(allowedRoles::contains);

        if (!hasAllowedRole) {
            String detectedRole = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .findFirst()
                    .orElse("UNKNOWN");
            throw new BadCredentialsException(portalErrorMessage + ":" + detectedRole);
        }

        return new UsernamePasswordAuthenticationToken(userDetails, userDetails.getPassword(), userDetails.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
