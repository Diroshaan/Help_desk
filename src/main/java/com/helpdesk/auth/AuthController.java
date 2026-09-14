package com.helpdesk.auth;

import com.helpdesk.auth.dto.CurrentUserResponse;
import com.helpdesk.common.user.repository.AppUserRepository;
import com.helpdesk.profile.service.ActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
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
     * Resolves a login email to an account of ANY type. Deliberately not
     * StudentRepository - see the comment on me() below for what that choice
     * was costing.
     */
    private final AppUserRepository appUserRepository;

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
                          ActivityLogService activityLogService,
                          AppUserRepository appUserRepository) {
        this.authenticationManager = authenticationManager;
        this.activityLogService = activityLogService;
        this.appUserRepository = appUserRepository;
    }

    // A simple record for the JSON request body: { "email": "...", "password": "..." }
    public record LoginRequest(String email, String password) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );

            // SESSION FIXATION DEFENCE - this one line, and why it matters.
            //
            // changeSessionId() requires an existing session - it throws
            // IllegalStateException otherwise. In apps where CSRF protection is
            // active, Spring creates one before login for exactly that reason
            // (to hold CSRF state); this app disables CSRF (see SecurityConfig),
            // so nothing touches the session before this point, and one has to
            // be created explicitly here first.
            //
            // getSession(true) creates a session if none exists yet (a no-op if
            // one already does), so this is safe to call unconditionally
            // regardless of what ran before it.
            //
            // The attack changeSessionId() defends against: plant a known
            // session id in a victim's browser first (a link carrying it, an
            // XSS on any page of the site, a shared machine), wait for them to
            // log in normally, and the attacker's pre-known id is now a valid
            // authenticated session for that victim's account. The attacker
            // never needs the password.
            //
            // changeSessionId() issues a new id and copies the session's
            // contents across, so whatever the attacker planted is now
            // worthless. Spring's built-in formLogin does this automatically;
            // this endpoint authenticates manually, so it has to do it
            // explicitly - and it must happen AFTER authenticate() succeeds and
            // BEFORE the security context is saved, or the context would be
            // written to the old id.
            httpRequest.getSession(true);
            httpRequest.changeSessionId();

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

            // Returns the account, not the string "Login successful".
            //
            // The old plain-text body was not wrong so much as useless: the
            // frontend has to decide where to send someone the instant they log
            // in - a student to their profile, an officer to the queue, an
            // administrator to the dashboard - and a success message carries
            // none of the information that decision needs. It had to make a
            // second request to find out, and the only endpoint that answered
            // ("who am I") worked for students alone.
            //
            // Returning the same body as GET /me means one round trip, one
            // response shape to handle, and no separate code path that can
            // disagree with the other one about who is signed in.
            return ResponseEntity.ok(currentUser(authentication.getName()));
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

    /**
     * Who is logged in right now - for ANY account type.
     *
     * This is the endpoint the frontend's session hook should call on startup,
     * and the reason it had to be written. The existing GET /api/students/me
     * resolves the caller through StudentService, which searches the students
     * table and nothing else, so an officer or administrator authenticated
     * perfectly well and then got 403 from it. useSession.jsx treats a 403 there
     * as "guest", so staff logged in and were immediately shown as signed out -
     * which made every officer and admin screen impossible to build or test,
     * even though the endpoints behind them were finished and merged.
     *
     * Why it belongs in AuthController rather than in a new SessionController or
     * alongside the student endpoints: the question "who is this session" is
     * about authentication, not about any one feature's data. Putting it under
     * /api/students said, structurally, that only students have sessions - and
     * that assumption is exactly what broke.
     *
     * 401, not 403, when the session is gone. Spring answers an anonymous
     * request to a protected path with 403 because neither formLogin nor
     * httpBasic is enabled (see SecurityConfig), and that is fine for endpoints
     * generally - but this one is specifically asked in order to find out
     * whether a session exists. 401 says "you are not authenticated", which is
     * the actual answer; 403 says "you are, but you may not", which is not.
     *
     * The empty case is reachable in practice: the session outlives the account
     * when an administrator deactivates a user, or a student deletes their own
     * profile, while their browser still holds a valid cookie.
     */
    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(Authentication authentication) {
        // Three ways there is nobody here, and all three mean the same 401.
        //
        // The AnonymousAuthenticationToken case is the one that is easy to miss:
        // because this path is permitAll (see SecurityConfig), Spring supplies an
        // anonymous token rather than null, and that token reports
        // isAuthenticated() == true. Checking only for null would therefore let a
        // logged-out visitor through to the lookup below with the principal name
        // "anonymousUser". It would still come back empty and still produce a
        // 401 - but by accident, because no account happens to have that email.
        // Saying so explicitly means the behaviour does not depend on that
        // coincidence.
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

    /**
     * Resolve a login email to the account behind it, whatever type it is.
     *
     * Goes through AppUserRepository, not StudentRepository, and that single
     * choice is the whole fix: AppUser is the root of a JOINED hierarchy, so
     * this query is polymorphic - Hibernate returns a Student, an Officer or an
     * Administrator already constructed as the right class, and
     * CurrentUserResponse.from() asks it for its role and its display name
     * without needing to know which it got.
     *
     * Returns null rather than throwing, because "no account for this session"
     * is an expected state here (see the comment on me() above), not an error
     * worth an exception and a stack trace.
     */
    private CurrentUserResponse currentUser(String email) {
        return appUserRepository.findByEmail(email)
                .map(CurrentUserResponse::from)
                .orElse(null);
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