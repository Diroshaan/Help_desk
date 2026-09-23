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

    /**
     * Tracks which sessions belong to which user, so a session can be ended
     * from outside the request that owns it.
     *
     * SessionRegistryImpl keeps this in memory, which is the right scope for a
     * single-instance application: the sessions themselves are in memory too, so
     * a restart clears both together and they cannot disagree. A clustered
     * deployment would need a shared store (Spring Session with Redis, say), and
     * that is a deployment decision rather than a code one.
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * Without this, SessionRegistryImpl never hears that a session was
     * destroyed and its map grows for the life of the process - every logout and
     * every timeout leaving an entry behind. It is a plain servlet listener that
     * forwards session lifecycle events into Spring's context.
     */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protection is designed around server-rendered forms with cookies.
                // For a stateless-style JSON API called from a separate frontend, it's
                // standard practice to disable it - acceptable trade-off for coursework.
                .csrf(csrf -> csrf.disable())

                // The H2 console renders inside an iframe, which Spring Security blocks by
                // default as clickjacking protection (X-Frame-Options: DENY).
                //
                // sameOrigin(), not disable(). The comment here used to say "only relax
                // this for /h2-console" while the code did the opposite: disable() removes
                // the header from EVERY response, so clickjacking protection was off for
                // the whole application - the login page and the profile page included -
                // to accommodate one development tool. Any site could have framed those
                // pages and tricked a signed-in student into clicking something they could
                // not see.
                //
                // sameOrigin() keeps the header and allows framing only from this
                // application's own origin, which is exactly what the H2 console needs and
                // nothing more. The console still works; everything else is protected
                // again. This is the header NFR 5.1 is asking for.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))

                .authorizeHttpRequests(auth -> auth
                        // Public: Spring's own internal forward to /error when a request
                        // fails - a controller throws, or the path just doesn't exist.
                        //
                        // Without this, that internal forward is itself just another
                        // request as far as authorizeHttpRequests is concerned, and falls
                        // through to ".anyRequest().authenticated()" below like anything
                        // else - so it gets rejected with 403 before BasicErrorController
                        // ever gets to report what actually went wrong. That means a real
                        // exception anywhere in the application - a NullPointerException, a
                        // bad SQL query, whatever - is masked as a permissions failure
                        // instead of surfacing its real status and message, and a request
                        // for a path that simply doesn't exist comes back as 403 instead of
                        // 404. Both make every bug in the project harder to diagnose.
                        //
                        // dispatcherTypeMatchers (rather than adding "/error" to the
                        // permitAll list below) only opens up this internal forward - it
                        // does not make /error reachable as a public URL a client could
                        // hit directly.
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()

                        // Public: browsing the dev database
                        .requestMatchers("/h2-console/**").permitAll()
                        // Public: creating a new account (you can't log in before you exist)
                        .requestMatchers(HttpMethod.POST, "/api/students").permitAll()
                        // Public: the login endpoint itself
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()

                        // Public: "am I logged in?"
                        //
                        // permitAll looks wrong on an endpoint that reports who
                        // the user is, so it is worth being precise about what it
                        // does and does not expose. The endpoint returns nothing
                        // about anybody except the caller's own session, and a
                        // caller with no session gets 401 and an empty body.
                        //
                        // It has to be public because of what the frontend does
                        // with it. The React app calls this once on startup to
                        // decide whether to render the app or the landing page,
                        // and that call happens before anyone has logged in. If
                        // it required authentication, Spring would answer the
                        // anonymous call with 403 - and a 403 is ambiguous here:
                        // it cannot be distinguished from "your session exists
                        // but is not allowed", which is a different situation
                        // needing different handling. Letting the request reach
                        // the controller means the answer comes back as a clean
                        // 401 with no body, which is unambiguous.
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").permitAll()

                        // Public: static frontend pages (register.html, login.html, and their
                        // CSS/JS) served straight out of src/main/resources/static, plus the
                        // React app's index.html and its built assets/ bundle, and images/media.
                        //
                        // Why this is needed: the catch-all rule at the bottom of this chain
                        // (".anyRequest().authenticated()") applies to EVERY request Spring
                        // Security sees - including requests for static files, not just
                        // /api/** endpoints. Without an explicit permitAll() here, a browser
                        // hitting GET /register.html gets rejected with 403 before the page
                        // ever loads, even though the API it POSTs to (POST /api/students) is
                        // already public - you'd be stuck unable to reach the registration
                        // form that lets you create an account in the first place. Static
                        // assets contain no sensitive data (unlike the API responses), so
                        // there's no privacy/ownership reason to gate them the way profile
                        // endpoints are gated above.
                        //
                        // More fundamentally, these all have to be public because they load
                        // BEFORE anyone has logged in: the browser fetches index.html plus its
                        // JS/CSS bundle to render the login screen itself, so if those requests
                        // required an authenticated session, nobody could ever reach a page
                        // capable of creating that session in the first place.
                        //
                        // (This used to be split into a second class, StaticResourceSecurityConfig,
                        // which used web.ignoring() for the same reason. Folded back in here as
                        // permitAll() so every access rule lives in one place.)
                        .requestMatchers(
                                "/", "/*.html", "/*.css", "/*.js",
                                "/css/**", "/js/**",
                                "/images/**",     // reserved for the avatar upload work in F1
                                "/media/**",      // the walkthrough video on the landing page
                                "/assets/**",     // the React bundle Vite writes on npm run build
                                "/index.html",    // the single page the React app is served from
                                "/favicon.ico"
                        ).permitAll()

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

                        // Officer-only: authoring the knowledge base (F5).
                        //
                        // WHY THESE ARE ENUMERATED ONE OPERATION AT A TIME,
                        // RATHER THAN WRITTEN AS ONE TIDY LINE
                        // -------------------------------------------------
                        // The obvious version of this block is:
                        //
                        //     .requestMatchers("/api/articles/**").hasRole("OFFICER")
                        //
                        // and it would be a bug. /api/articles/** is not an
                        // officer-only area - four different audiences share
                        // that prefix:
                        //
                        //   GET    /api/articles                 any logged-in user (search)
                        //   GET    /api/articles/{id}            any logged-in user (read)
                        //   GET    /api/articles/bookmarked      students, their saved list
                        //   POST   /api/articles/{id}/bookmark   students, saving one
                        //   DELETE /api/articles/{id}/bookmark   students, unsaving one
                        //   POST   /api/articles                 OFFICERS, authoring
                        //
                        // A blanket rule would 403 students out of searching and
                        // bookmarking - the two things the whole feature exists
                        // to give them. The path prefix does not describe the
                        // permission; the specific operation does. So one
                        // matcher per operation, and the student-facing paths
                        // are deliberately absent so they fall through to
                        // .anyRequest().authenticated() below.
                        //
                        // ORDER IS LOAD-BEARING. These are evaluated top to
                        // bottom and the FIRST match wins, so /api/articles/manage
                        // must be listed before any broader pattern that would
                        // also match it.
                        //
                        // "*" matches ONE path segment; "**" matches any number.
                        // /api/articles/* is therefore /api/articles/7 but NOT
                        // /api/articles/7/publish - which is exactly why each
                        // sub-resource gets its own line rather than being
                        // swept up by a "**".
                        //
                        // Without these lines every path below falls through to
                        // the catch-all, which requires only that SOMEBODY is
                        // logged in - meaning any student could create, edit,
                        // publish and archive knowledge base articles.
                        .requestMatchers(HttpMethod.GET,    "/api/articles/manage").hasRole("OFFICER")
                        .requestMatchers(HttpMethod.POST,   "/api/articles").hasRole("OFFICER")
                        .requestMatchers(HttpMethod.PUT,    "/api/articles/*").hasRole("OFFICER")
                        .requestMatchers(HttpMethod.POST,   "/api/articles/*/publish").hasRole("OFFICER")
                        .requestMatchers(HttpMethod.POST,   "/api/articles/*/archive").hasRole("OFFICER")
                        .requestMatchers(HttpMethod.POST,   "/api/articles/*/related").hasRole("OFFICER")
                        .requestMatchers(HttpMethod.DELETE, "/api/articles/*/related/*").hasRole("OFFICER")

                        // Officer/Admin-only: aggregate feedback statistics.
                        //
                        // This endpoint takes a category and returns satisfaction
                        // figures across every student's feedback for it. It has
                        // no owner and no role check of its own, and it does not
                        // sit under /api/admin/**, so until now it fell through
                        // to the catch-all - meaning any logged-in student could
                        // read the help desk's internal performance analytics.
                        // Nothing there identifies an individual, so this is a
                        // disclosure problem rather than a privacy breach, but it
                        // is staff information and it was readable by everyone.
                        .requestMatchers(HttpMethod.GET, "/api/feedback/summary").hasAnyRole("OFFICER", "ADMIN")

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

                        // REGISTERING SESSIONS, so they can be ended later.
                        //
                        // maximumSessions is not here to LIMIT anything - -1
                        // means unlimited, and a student signing in from a
                        // laptop and a phone at once is perfectly reasonable.
                        // It is here because configuring it is what puts
                        // ConcurrentSessionFilter into the filter chain, and
                        // that filter is what notices a session has been marked
                        // expired and invalidates it.
                        //
                        // Without this block, SessionRevoker would call
                        // expireNow() on sessions nobody ever checks, and a
                        // suspended user would carry on working. See
                        // auth/SessionRevoker.java for why that matters.
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry())

                        // What an expired session gets back.
                        //
                        // The default writes an HTML message, which is wrong for
                        // a JSON API - the frontend would try to parse a page.
                        // 401 is the accurate answer ("I no longer know who you
                        // are") AND it is exactly what api.js already watches
                        // for: a 401 on any request signs the user out cleanly
                        // and sends them to the login page with an explanation.
                        // So suspending an account now produces a tidy sign-out
                        // on that user's next click, with no extra frontend code.
                        .expiredSessionStrategy(event ->
                                event.getResponse().setStatus(HttpServletResponse.SC_UNAUTHORIZED))
                );

        return http.build();
    }
}
