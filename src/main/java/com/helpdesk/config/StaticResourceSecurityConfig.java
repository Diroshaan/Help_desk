package com.helpdesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

/**
 * Opens up the shared static assets (/css and /js) to everyone.
 *
 * <p>Why this class exists:</p>
 *
 * <p>{@code SecurityConfig} permits {@code /*.html}, which covers welcome.html,
 * login.html and the rest. It does <b>not</b> cover {@code /css/app.css} or
 * {@code /js/app.js}, because those live in subfolders and the single-star
 * pattern only matches one path segment. Every page used to carry its own CSS
 * inside a &lt;style&gt; tag, so the browser never asked for a separate file and
 * the gap never showed. Now that all five pages share one stylesheet and one
 * script, the browser requests them — and an anonymous visitor on the login
 * page is refused with 403, which is why the pages rendered with no styling
 * at all.</p>
 *
 * <p>{@code web.ignoring()} takes these paths out of the security filter chain
 * completely. That is the right call here and nowhere else: a stylesheet and a
 * script file contain no user data, need no session, and must load <i>before</i>
 * anyone has logged in. Application endpoints are untouched — {@code /api/**}
 * still goes through the normal chain in {@code SecurityConfig}.</p>
 *
 * <p>Spring Security prints an informational warning at startup suggesting
 * permitAll() instead of ignoring(). That warning is expected and harmless for
 * static assets; ignoring them also skips the filter chain work on every asset
 * request, which is faster.</p>
 */
@Configuration
public class StaticResourceSecurityConfig {

    @Bean
    public WebSecurityCustomizer staticResourceSecurityCustomizer() {
        return (WebSecurity web) -> web.ignoring().requestMatchers(
                "/css/**",
                "/js/**",
                "/images/**",     // reserved for the avatar upload work in F1
                "/favicon.ico"
        );
    }
}
