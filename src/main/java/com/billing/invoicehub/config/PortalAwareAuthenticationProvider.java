package com.billing.invoicehub.config;

import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.PortalType;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.service.CustomUserDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Custom {@link AuthenticationProvider} enforcing domain-scoped authentication
 * and portal-role security,
 * with automatic, transparent password hash upgrade to current security
 * standards upon successful authentication.
 */
public class PortalAwareAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(PortalAwareAuthenticationProvider.class);

    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final AppUserRepository userRepository;
    private final PortalType portalType;
    private final String targetDomain; // "VENDOR" or "INTERNAL"
    private final Set<String> allowedRoles;
    private final String portalErrorMessage;

    public PortalAwareAuthenticationProvider(CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            AppUserRepository userRepository,
            PortalType portalType,
            String targetDomain,
            Set<String> allowedRoles,
            String portalErrorMessage) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.portalType = portalType;
        this.targetDomain = targetDomain;
        this.allowedRoles = allowedRoles;
        this.portalErrorMessage = portalErrorMessage;
    }

    public PortalAwareAuthenticationProvider(CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            PortalType portalType,
            String targetDomain,
            Set<String> allowedRoles,
            String portalErrorMessage) {
        this(userDetailsService, passwordEncoder, null, portalType, targetDomain, allowedRoles, portalErrorMessage);
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

        // 2. Validate password (supporting current DelegatingPasswordEncoder,
        // un-prefixed BCrypt, or legacy formats)
        String storedPassword = userDetails.getPassword();
        boolean matches = passwordEncoder.matches(rawPassword, storedPassword);

        if (!matches && isLegacyMatch(rawPassword, storedPassword)) {
            matches = true;
        }

        if (!matches) {
            throw new BadCredentialsException("Invalid username or password");
        }

        // 3. Transparent password hash migration: if stored hash is un-prefixed or
        // weaker, upgrade to {bcrypt}
        if (userRepository != null
                && (passwordEncoder.upgradeEncoding(storedPassword) || !storedPassword.startsWith("{bcrypt}"))) {
            try {
                AppUser user = userRepository.findFirstByUsernameAndUserDomain(userDetails.getUsername(), targetDomain)
                        .orElseGet(() -> userRepository
                                .findFirstByEmailIgnoreCaseAndUserDomain(userDetails.getUsername(), targetDomain)
                                .orElse(null));
                if (user != null) {
                    user.setPassword(passwordEncoder.encode(rawPassword));
                    userRepository.save(user);
                    log.info("Transparently migrated password hash to modern BCrypt for user '{}' in domain '{}'",
                            user.getUsername(), targetDomain);
                }
            } catch (Exception ex) {
                log.warn("Failed to transparently upgrade password hash for user '{}': {}",
                        userDetails.getUsername(), ex.getMessage());
            }
        }

        // 4. Validate role allowed for this specific portal
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

        return new UsernamePasswordAuthenticationToken(userDetails, userDetails.getPassword(),
                userDetails.getAuthorities());
    }

    private boolean isLegacyMatch(String rawPassword, String storedPassword) {
        if (storedPassword == null || rawPassword == null) {
            return false;
        }
        // Direct plain-text match (legacy historical migration safeguard)
        if (storedPassword.equals(rawPassword)) {
            return true;
        }
        // Legacy MD5 hex match
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            if (sb.toString().equalsIgnoreCase(storedPassword)) {
                return true;
            }
        } catch (Exception ignored) {
        }

        // Legacy SHA-1 hex match
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            if (sb.toString().equalsIgnoreCase(storedPassword)) {
                return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
