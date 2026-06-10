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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(CustomUserDetailsService userDetailsService,
                                                         PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    @Order(value = 1)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http,
                                                        AuthenticationProvider authenticationProvider, RoleBasedAuthenticationSuccessHandler successHandler,
                                                        RoleBasedLogoutSuccessHandler logoutSuccessHandler) throws Exception {
        http.securityMatcher("/admin/**")
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login", "/error", "/css/**", "/js/**", "/favicon.ico").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .successHandler(successHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(logoutSuccessHandler))
                .exceptionHandling(ex -> ex.accessDeniedPage("/admin/login?denied=true"))
                .csrf(Customizer.withDefaults())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'self'; img-src 'self' https: data:; script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; font-src 'self' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; frame-ancestors 'self';")))
                .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    @Order(value = 2)
    public SecurityFilterChain appSecurityFilterChain(HttpSecurity http, AuthenticationProvider authenticationProvider,
                                                      RoleBasedAuthenticationSuccessHandler successHandler, RoleBasedAuthenticationFailureHandler failureHandler,
                                                      RoleBasedLogoutSuccessHandler logoutSuccessHandler) throws Exception {
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
                                "/images/**",
                                "/webjars/**",
                                "/favicon.ico")
                        .permitAll()
                        .requestMatchers("/api/purchase-orders/**").authenticated()
                        .requestMatchers("/clients", "/dashboard").hasRole("ADMIN")
                        .requestMatchers("/saveClient").hasRole("ADMIN")
                        .requestMatchers("/vendor-tickets", "/vendor-tickets/**").hasRole("USER")
                        .requestMatchers("/invoice", "/invoice/**", "/saveInvoice", "/updateInvoice",
                                "/deleteInvoice/**")
                        .hasRole("USER")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(logoutSuccessHandler))
                .exceptionHandling(ex -> ex.accessDeniedPage("/login?denied=true"))
                .csrf(Customizer.withDefaults())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'self'; img-src 'self' https: data:; script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; font-src 'self' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; frame-ancestors 'self';")))
                .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }
}