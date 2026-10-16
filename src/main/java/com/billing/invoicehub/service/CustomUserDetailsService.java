package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AppUserRepository;
import java.util.Collection;
import java.util.stream.Collectors;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;

    public CustomUserDetailsService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findFirstByUsername(username)
                .orElseGet(() -> userRepository.findFirstByEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username)));

        return buildUserDetails(user);
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserByUsernameAndDomain(String identifier, String userDomain) throws UsernameNotFoundException {
        if (identifier == null || identifier.isBlank()) {
            throw new UsernameNotFoundException("User identifier is empty");
        }
        String clean = identifier.trim();

        AppUser user = userRepository.findFirstByUsernameAndUserDomain(clean, userDomain)
                .orElseGet(() -> userRepository.findFirstByEmailIgnoreCaseAndUserDomain(clean, userDomain)
                .orElseThrow(() -> new UsernameNotFoundException("User not found in " + userDomain + " domain: " + identifier)));

        return buildUserDetails(user);
    }

    private UserDetails buildUserDetails(AppUser user) {
        boolean isVendor = "VENDOR".equalsIgnoreCase(user.getUserDomain());
        if (isVendor && user.getRegistrationDate() != null && !user.isVerified()) {
            throw new DisabledException("Account pending verification");
        }

        Collection<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toSet());

        return new User(user.getUsername(), user.getPassword(), user.isEnabled(), true, true, true, authorities);
    }
}
