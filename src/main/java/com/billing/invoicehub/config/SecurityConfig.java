package com.billing.invoicehub.config;

import com.billing.invoicehub.entity.PortalType;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.StandardPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${app.security.csrf.enabled:true}")
    private boolean csrfEnabled;

    @Bean
    public PasswordEncoder passwordEncoder() {
        String defaultId = "bcrypt";
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        encoders.put("noop", NoOpPasswordEncoder.getInstance());
        encoders.put("sha256", new StandardPasswordEncoder());

        DelegatingPasswordEncoder delegatingPasswordEncoder =
                new DelegatingPasswordEncoder(defaultId, encoders);
        // Requirement 1: existing hashes in the DB are unprefixed BCrypt hashes ($2a$ / $2b$).
        // Setting default password encoder for matches ensures all existing users authenticate without issue.
        delegatingPasswordEncoder.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        return delegatingPasswordEncoder;
    }

    @Bean
    public DaoAuthenticationProvider baseDaoProvider(CustomUserDetailsService userDetailsService,
                                                     PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    // ─── Security filter chains ───────────────────────────────────────────────

    /**
     * Portal 3: Admin portal chain — handles /admin/** exclusively.
     * Processing URL: POST /admin/authenticate
     */
    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(
            HttpSecurity http,
            RoleBasedAuthenticationSuccessHandler successHandler,
            RoleBasedAuthenticationFailureHandler failureHandler,
            RoleBasedLogoutSuccessHandler logoutSuccessHandler,
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            AppUserRepository userRepository) throws Exception {

        AuthenticationProvider adminProvider = new PortalAwareAuthenticationProvider(
                userDetailsService,
                passwordEncoder,
                userRepository,
                PortalType.ADMIN,
                "INTERNAL",
                Set.of("ROLE_ADMIN", "ROLE_ACCOUNTS", "ROLE_SUPER_ADMIN"),
                RoleBasedAuthenticationFailureHandler.MSG_NON_ADMIN_ON_ADMIN_PORTAL);

        http.securityMatcher("/admin/**")
                .authenticationManager(new ProviderManager(adminProvider))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login", "/admin/authenticate",
                                "/error", "/css/**", "/js/**", "/favicon.ico")
                        .permitAll()
                        .requestMatchers("/admin/change-password").authenticated()
                        .requestMatchers("/admin/users/**", "/admin/api/maintenance-banner").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/admin/vendors/*/verify", "/admin/vendors/*/reject").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .anyRequest().hasAnyRole("ADMIN", "ACCOUNTS", "SUPER_ADMIN"))
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/authenticate")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(logoutSuccessHandler))
                .exceptionHandling(ex -> ex.accessDeniedPage("/admin/login?denied=true"))
                .csrf(csrfEnabled ? Customizer.withDefaults() : AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'self'; img-src 'self' https: data:; " +
                                        "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +
                                        "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +
                                        "font-src 'self' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +
                                        "frame-ancestors 'self';")))
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    /**
     * Portal 1: Vendor / App portal chain — handles all other non-admin routes.
     * Processing URL: POST /vendor/authenticate
     */
    @Bean
    @Order(2)
    public SecurityFilterChain appSecurityFilterChain(
            HttpSecurity http,
            RoleBasedAuthenticationSuccessHandler successHandler,
            RoleBasedAuthenticationFailureHandler failureHandler,
            RoleBasedLogoutSuccessHandler logoutSuccessHandler,
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            AppUserRepository userRepository) throws Exception {

        AuthenticationProvider vendorProvider = new PortalAwareAuthenticationProvider(
                userDetailsService,
                passwordEncoder,
                userRepository,
                PortalType.VENDOR,
                "VENDOR",
                Set.of("ROLE_VENDOR"),
                RoleBasedAuthenticationFailureHandler.MSG_WRONG_PORTAL);

        http.authenticationManager(new ProviderManager(vendorProvider))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/login",
                                "/vendor/login",
                                "/vendor/authenticate",
                                "/signup",
                                "/register",
                                "/api/register/**",
                                "/api/maintenance-banner",
                                "/forgot-password",
                                "/verify-otp",
                                "/reset-password",
                                "/faq",
                                "/contact",
                                "/error",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/webjars/**",
                                "/favicon.ico")
                        .permitAll()
                        .requestMatchers("/change-password").authenticated()
                        .requestMatchers("/clients", "/clients/**", "/saveClient", "/updateClient", "/deleteClient/**", "/dashboard").hasRole("ADMIN")
                        .requestMatchers("/vendor-tickets", "/vendor-tickets/**").hasRole("VENDOR")
                        .requestMatchers("/invoice", "/invoice/**",
                                "/saveInvoice", "/updateInvoice", "/deleteInvoice/**").hasRole("VENDOR")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/vendor/login")
                        .loginProcessingUrl("/vendor/authenticate")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(logoutSuccessHandler))
                .exceptionHandling(ex -> ex.accessDeniedPage("/vendor/login?denied=true"))
                .csrf(csrfEnabled ? Customizer.withDefaults() : AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'self'; img-src 'self' https: data:; " +
                                        "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +
                                        "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +
                                        "font-src 'self' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +
                                        "frame-ancestors 'self';")))
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
