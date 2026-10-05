package com.helpdesk.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Security setup: session-based login and the role-based access rules for the API.
 * There is no formLogin or httpBasic, so this stays a plain JSON API; users sign in
 * through POST /api/auth/login in AuthController.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // BCrypt is slow on purpose and salts each hash, which makes brute force harder.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Lets AuthController call authenticate(...) itself in the login endpoint.
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Keeps track of each user's sessions so we can end them from outside the request
     * (e.g. when an account is suspended). In memory is fine for a single server.
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    // Tells the registry when a session is destroyed, otherwise old entries pile up.
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF tokens are built for server-rendered forms. This is a JSON API used
                // by a separate frontend, so we turn it off (an accepted trade-off here).
                .csrf(csrf -> csrf.disable())

                // sameOrigin() rather than disable(): the H2 console needs to run in a frame,
                // but every other page keeps its clickjacking protection (NFR 5.1).
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))

                .authorizeHttpRequests(auth -> auth
                        // Let Spring's internal error/forward dispatches through, so real errors
                        // and 404s are reported properly instead of showing up as 403.
                        // This doesn't make /error a public URL.
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()

                        // Must stay first. Spring answers HEAD by running the GET handler, so HEAD
                        // would slip past our GET-only role rules. The frontend never sends HEAD.
                        .requestMatchers(HttpMethod.HEAD, "/api/**").denyAll()

                        // Public: dev database console, registration and login.
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/students").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()

                        // Public so the frontend can ask "am I logged in?" on startup. It only
                        // ever describes the caller's own session; no session gives a clean 401.
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").permitAll()

                        // Public: static files and the React bundle. They load before anyone
                        // has logged in and hold no private data.
                        .requestMatchers(
                                "/", "/*.html", "/*.css", "/*.js",
                                "/css/**", "/js/**",
                                "/images/**",
                                "/media/**",      // landing page video
                                "/assets/**",     // React build output
                                "/index.html",
                                "/favicon.ico"
                        ).permitAll()

                        // Role rules. The first matching rule wins, so these must come before
                        // the anyRequest() catch-all. A wrong role gets 403.
                        .requestMatchers("/api/queue/**").hasRole("OFFICER")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // An officer's own profile; separate from /api/admin/officers, where admins provision officers.
                        .requestMatchers("/api/officers/**").hasRole("OFFICER")

                        // Knowledge base authoring is officer-only, listed one operation at a time.
                        // A blanket /api/articles/** rule would lock students out of searching,
                        // reading and bookmarking, which fall through to the catch-all.
                        // "*" is one path segment, so each sub-resource needs its own line.
                        .requestMatchers(HttpMethod.GET,    "/api/articles/manage").hasRole("OFFICER")
                        .requestMatchers(HttpMethod.POST,   "/api/articles").hasRole("OFFICER")
                        .requestMatchers(HttpMethod.PUT,    "/api/articles/*").hasRole("OFFICER")
                        .requestMatchers(HttpMethod.POST,   "/api/articles/*/publish").hasRole("OFFICER")
                        .requestMatchers(HttpMethod.POST,   "/api/articles/*/archive").hasRole("OFFICER")
                        .requestMatchers(HttpMethod.POST,   "/api/articles/*/related").hasRole("OFFICER")
                        .requestMatchers(HttpMethod.DELETE, "/api/articles/*/related/*").hasRole("OFFICER")

                        // Feedback statistics are staff information, not for students.
                        .requestMatchers(HttpMethod.GET, "/api/feedback/summary").hasAnyRole("OFFICER", "ADMIN")

                        // Only staff can list every student. GET /api/students/{id} falls through
                        // to the catch-all; StudentController checks it is the caller's own id.
                        .requestMatchers(HttpMethod.GET, "/api/students").hasAnyRole("OFFICER", "ADMIN")

                        // Everything else just needs a logged-in user.
                        .anyRequest().authenticated()
                )

                // Session-based auth: a session is created when the user logs in.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)

                        // -1 means no limit on sessions per user. Setting it adds the filter
                        // that kills sessions SessionRevoker marks as expired (e.g. suspended users).
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry())

                        // An expired session gets a plain 401, which the frontend already
                        // handles by signing the user out.
                        .expiredSessionStrategy(event ->
                                event.getResponse().setStatus(HttpServletResponse.SC_UNAUTHORIZED))
                );

        return http.build();
    }
}
