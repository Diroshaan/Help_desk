package com.helpdesk.auth;

import com.helpdesk.auth.dto.CurrentUserResponse;
import com.helpdesk.auth.dto.PasswordChangeRequest;
import com.helpdesk.common.user.repository.AppUserRepository;
import com.helpdesk.profile.service.ActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login, logout, "who am I" and password change for every account type.
 * Login is done by hand (no formLogin), so session fixation protection and session
 * registration are done here explicitly.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;

    private final AppUserRepository appUserRepository;

    // Lets SessionRevoker find and end this user's sessions later.
    private final SessionRegistry sessionRegistry;

    // Records successful logins on the student's activity log.
    private final ActivityLogService activityLogService;

    private final PasswordService passwordService;

    // Saves the login into the HTTP session so later requests stay signed in.
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @Autowired
    public AuthController(AuthenticationManager authenticationManager,
                          ActivityLogService activityLogService,
                          AppUserRepository appUserRepository,
                          SessionRegistry sessionRegistry,
                          PasswordService passwordService) {
        this.authenticationManager = authenticationManager;
        this.activityLogService = activityLogService;
        this.appUserRepository = appUserRepository;
        this.sessionRegistry = sessionRegistry;
        this.passwordService = passwordService;
    }

    // email may also be a Student ID
    public record LoginRequest(String email, String password) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );

            // Session fixation defence: give the session a new id after login, so an id planted
            // by an attacker before login is useless. CSRF is off, so no session exists yet and we
            // create one first (changeSessionId() needs one).
            httpRequest.getSession(true);
            httpRequest.changeSessionId();

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            securityContextRepository.saveContext(context, httpRequest, httpResponse);

            // Spring only registers sessions in formLogin, so we do it here; without it SessionRevoker
            // silently finds nothing. Must come after changeSessionId() so the new id is recorded.
            sessionRegistry.registerNewSession(
                    httpRequest.getSession().getId(), authentication.getPrincipal());

            // "Quietly" so a failed log write can't turn a successful login into a 500. Uses the
            // principal name (always the email) because the student may have typed a Student ID.
            activityLogService.recordLoginQuietly(authentication.getName());

            // Same body as GET /me, so the frontend can route by role straight away.
            return ResponseEntity.ok(currentUser(authentication.getName()));
        } catch (DisabledException e) {
            // correct password but the account is inactive (.disabled() in StudentUserDetailsService)
            return ResponseEntity.status(401).body("This account has been deactivated");
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body("Invalid Student ID, email or password");
        }
    }

    /**
     * Current user for any account type; the frontend calls this on startup.
     * Returns 401 (not 403) when there is no session or the account no longer exists.
     */
    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(Authentication authentication) {
        // permitAll path, so a logged-out visitor arrives with an anonymous token, not null.
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(401).build();
        }
        CurrentUserResponse user = currentUser(authentication.getName());
        return (user == null)
                ? ResponseEntity.status(401).build()
                : ResponseEntity.ok(user);
    }

    // null when the session's account has been deleted - an expected case, not an error.
    private CurrentUserResponse currentUser(String email) {
        return appUserRepository.findByEmail(email)
                .map(CurrentUserResponse::from)
                .orElse(null);
    }

    // Any signed-in user; rules are in PasswordService. 204 so nothing about the account is returned.
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request,
                                               Authentication authentication,
                                               HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        passwordService.changePassword(authentication.getName(),
                session == null ? null : session.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        // Logouts aren't written to the activity log on purpose; logins are enough.
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Logged out");
    }
}