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
 *
 * This class also defines the role-based access rules (RBAC) - which paths
 * need no login at all, which just need SOME logged-in user, and which need a
 * SPECIFIC role (e.g. only an Officer can use the support queue). See the
 * comments inside the authorizeHttpRequests(...) block below for details on
 * each rule.
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

                        // Role-based access rules (RBAC) below.
                        //
                        // Being logged in (authenticated) is not the same as being ALLOWED to
                        // use a given feature - a Student and an Officer both "authenticate"
                        // the same way via /api/auth/login, but only an Officer should be able
                        // to work the support queue, and only an Admin should reach admin
                        // features. hasRole("X") checks that the logged-in user's authorities
                        // (set up in StudentUserDetailsService as "ROLE_" + student.getRole())
                        // include "ROLE_X". If they don't, Spring Security rejects the request
                        // with 403 Forbidden before it ever reaches the controller.
                        //
                        // These rules are matched top-to-bottom, and the FIRST matching rule
                        // wins - that's why the specific "/api/queue/**" and "/api/admin/**"
                        // rules must be listed here, before the catch-all ".anyRequest()" rule
                        // below. If they were listed after it, the catch-all would already have
                        // matched every request first and these two would never be checked.

                        // Officer-only: managing/working the student support queue (F4).
                        .requestMatchers("/api/queue/**").hasRole("OFFICER")
                        // Admin-only: system administration features (F6).
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Officer/Admin-only: listing every student's profile.
                        //
                        // Why this needs restricting: GET /api/students returns every
                        // student's data in one response, with no filtering. That's fine for
                        // an Officer or Admin who legitimately needs to look students up, but
                        // it's a privacy problem if any logged-in Student could pull the whole
                        // student directory just by calling this endpoint - a Student should
                        // only ever see/edit their own profile, not browse everyone else's.
                        // Note this exact-path matcher only covers the list endpoint
                        // (GET /api/students); GET /api/students/{id} falls through to the
                        // catch-all rule below instead, which only requires SOME logged-in
                        // user - the extra "is this actually your own id (or are you staff)?"
                        // check for that single-student lookup is done in StudentController's
                        // findById(), not here, since it depends on the {id} in the URL rather
                        // than the path shape alone.
                        .requestMatchers(HttpMethod.GET, "/api/students").hasAnyRole("OFFICER", "ADMIN")

                        // Catch-all: anything not covered by a rule above just needs SOME
                        // logged-in user - no specific role required. This is what protects
                        // endpoints like /api/students/{id} once a session exists.
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
