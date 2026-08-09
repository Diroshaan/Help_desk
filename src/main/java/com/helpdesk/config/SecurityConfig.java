package com.helpdesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * F7 - Authentication & session handling (shared/cross-cutting).
 *
 * This is deliberately NOT using Spring's default browser login page (formLogin)
 * or a login popup (httpBasic). Since neither is enabled below, unauthenticated
 * requests to protected endpoints get a plain 403 Forbidden - correct behaviour
 * for a JSON REST API meant to serve a separate frontend, matching the System
 * Overview Diagram in the proposal (API Request (JSON) between frontend and backend).
 *
 * Real login happens through AuthController's POST /api/auth/login instead.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * BCrypt is the industry-standard way to hash passwords - it's slow by design
     * (to resist brute-force attacks) and automatically handles salting for you.
     * Spring Security auto-detects this bean and uses it wherever a password
     * needs to be checked or encoded, including inside StudentService.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Exposes Spring Security's authentication machinery so AuthController can
     * call authenticationManager.authenticate(...) directly in the login endpoint.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protection is designed around server-rendered forms with cookies.
                // For a stateless-style JSON API called from a separate frontend, it's
                // standard practice to disable it - acceptable trade-off for coursework.
                .csrf(csrf -> csrf.disable())

                // H2 console renders inside an iframe, which Spring Security blocks by
                // default (clickjacking protection). Only relax this for /h2-console.
                .headers(headers -> headers.frameOptions(frame -> frame.disable()))

                .authorizeHttpRequests(auth -> auth
                        // Public: browsing the dev database
                        .requestMatchers("/h2-console/**").permitAll()
                        // Public: creating a new account (you can't log in before you exist)
                        .requestMatchers(HttpMethod.POST, "/api/students").permitAll()
                        // Public: the login endpoint itself
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()

                        // --- Extend here as F4/F6 add Officer and Admin endpoints, e.g.: ---
                        // .requestMatchers("/api/queue/**").hasRole("OFFICER")
                        // .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Everything else requires a logged-in session
                        .anyRequest().authenticated()
                )

                // Use a session once the user logs in (created on demand, not forced
                // up front) - this is what "session-based authentication" means here.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                );

        return http.build();
    }
}
