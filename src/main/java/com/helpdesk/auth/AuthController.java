package com.helpdesk.auth;

import com.helpdesk.profile.service.ActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * F7 - Login/logout endpoints (shared/cross-cutting).
 *
 * POST /api/auth/login  { "email": "...", "password": "..." }  -> creates a session
 * POST /api/auth/logout                                        -> ends the session
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;

    /**
     * Used only to record a successful sign-in on the student's activity log
     * (F1 - "Dashboard & Activity View").
     *
     * This is the one place outside com.helpdesk.profile that writes to the log,
     * and it is here rather than in StudentService because this is where the
     * event actually happens - only this class knows that an authentication
     * attempt succeeded. Recording it from anywhere else would mean inferring a
     * login from something that is not one.
     *
     * The dependency points from auth into profile, which is the direction that
     * already exists (StudentUserDetailsService reads Student), so this adds no
     * new coupling between packages.
     */
    private final ActivityLogService activityLogService;

    // Handles actually persisting the authenticated user into the HTTP session,
    // so subsequent requests (with the same session cookie) are recognised as
    // logged in without needing to send the password again.
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @Autowired
    public AuthController(AuthenticationManager authenticationManager,
                          ActivityLogService activityLogService) {
        this.authenticationManager = authenticationManager;
        this.activityLogService = activityLogService;
    }

    // A simple record for the JSON request body: { "email": "...", "password": "..." }
    public record LoginRequest(String email, String password) {}

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest request,
                                         HttpServletRequest httpRequest,
                                         HttpServletResponse httpResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            // Without this line, the login would succeed for this one request only -
            // the session wouldn't remember it on the next request.
            securityContextRepository.saveContext(context, httpRequest, httpResponse);

            // Recorded AFTER the session is established, and deliberately using
            // the "quietly" variant that swallows its own failures.
            //
            // By this point the student IS logged in - authentication succeeded
            // and the session exists. Letting a failed INSERT propagate would
            // turn that into a 500 and shut them out of an account they have
            // just proved they own, over a bookkeeping row. The event is worth
            // recording; it is not worth denying access over. See
            // ActivityLogService.recordLoginQuietly() for the contrast with
            // the profile-update entries, which deliberately do NOT swallow.
            //
            // authentication.getName() is the email the account authenticated
            // with, which is what the log needs to find the student - note the
            // student may have typed their Student ID instead (see
            // StudentUserDetailsService), so request.email() is not reliable
            // here and the authenticated principal is.
            activityLogService.recordLoginQuietly(authentication.getName());

            return ResponseEntity.ok("Login successful");
        } catch (DisabledException e) {
            // Thrown by DaoAuthenticationProvider because StudentUserDetailsService
            // builds the UserDetails with .disabled(!student.isActive()) - so an
            // account that self-deactivated (see StudentService.deactivate) fails
            // authentication here even with the correct password. Without this
            // catch block, DisabledException would fall through as an unhandled
            // exception and surface as a generic 500 instead of a clear rejection.
            return ResponseEntity.status(401).body("This account has been deactivated");
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body("Invalid Student ID, email or password");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        // Deliberately NOT logged.
        //
        // A sign-out is not something a student needs to check up on: it is
        // never surprising and never evidence of anything. Every login already
        // implies the previous session ended, so recording both would double
        // the size of the busiest part of the history while halving how much
        // of it is worth reading. If suspicious-activity review is ever a
        // requirement, this is where it would be added.
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Logged out");
    }
}
