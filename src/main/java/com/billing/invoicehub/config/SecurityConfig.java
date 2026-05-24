/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.billing.invoicehub.config.RoleBasedAuthenticationFailureHandler
 *  com.billing.invoicehub.config.RoleBasedAuthenticationSuccessHandler
 *  com.billing.invoicehub.config.RoleBasedLogoutSuccessHandler
 *  com.billing.invoicehub.config.SecurityConfig
 *  com.billing.invoicehub.service.CustomUserDetailsService
 *  org.springframework.context.annotation.Bean
 *  org.springframework.context.annotation.Configuration
 *  org.springframework.core.annotation.Order
 *  org.springframework.security.authentication.AuthenticationProvider
 *  org.springframework.security.authentication.dao.DaoAuthenticationProvider
 *  org.springframework.security.config.Customizer
 *  org.springframework.security.config.annotation.web.builders.HttpSecurity
 *  org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
 *  org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
 *  org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer$AuthorizedUrl
 *  org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer
 *  org.springframework.security.core.userdetails.UserDetailsService
 *  org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
 *  org.springframework.security.crypto.password.PasswordEncoder
 *  org.springframework.security.web.SecurityFilterChain
 *  org.springframework.security.web.authentication.AuthenticationFailureHandler
 *  org.springframework.security.web.authentication.AuthenticationSuccessHandler
 *  org.springframework.security.web.authentication.logout.LogoutSuccessHandler
 */
package com.billing.invoicehub.config;

import com.billing.invoicehub.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(CustomUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    @Order(value=1)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http, AuthenticationProvider authenticationProvider, RoleBasedAuthenticationSuccessHandler successHandler, RoleBasedLogoutSuccessHandler logoutSuccessHandler) throws Exception {
        http.securityMatcher("/admin/**")
            .authenticationProvider(authenticationProvider)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/login", "/error", "/css/**", "/js/**", "/uploads/**").permitAll()
                .anyRequest().hasRole("ADMIN")
            )
            .formLogin(form -> form
                .loginPage("/admin/login")
                .loginProcessingUrl("/admin/login")
                .successHandler(successHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler(logoutSuccessHandler)
            )
            .exceptionHandling(ex -> ex.accessDeniedPage("/admin/login?denied=true"))
            .csrf(Customizer.withDefaults())
            .headers(Customizer.withDefaults())
            .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    @Order(value=2)
    public SecurityFilterChain appSecurityFilterChain(HttpSecurity http, AuthenticationProvider authenticationProvider, RoleBasedAuthenticationSuccessHandler successHandler, RoleBasedAuthenticationFailureHandler failureHandler, RoleBasedLogoutSuccessHandler logoutSuccessHandler) throws Exception {
        http.authenticationProvider(authenticationProvider)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/login",
                    "/signup",
                    "/register",
                    "/forgot-password",
                    "/verify-otp",
                    "/reset-password",
                    "/faq",
                    "/contact",
                    "/error",
                    "/css/**",
                    "/js/**",
                    "/uploads/**",
                    "/images/**",
                    "/webjars/**",
                    "/api/purchase-orders/**"
                ).permitAll()
                .requestMatchers("/clients", "/dashboard").hasRole("ADMIN")
                .requestMatchers("/saveClient").hasRole("ADMIN")
                .requestMatchers("/vendor-tickets", "/vendor-tickets/**").hasRole("USER")
                .requestMatchers("/invoice", "/saveInvoice").hasAnyRole("USER", "ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler(logoutSuccessHandler)
            )
            .exceptionHandling(ex -> ex.accessDeniedPage("/login?denied=true"))
            .csrf(Customizer.withDefaults())
            .headers(Customizer.withDefaults())
            .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }
}

