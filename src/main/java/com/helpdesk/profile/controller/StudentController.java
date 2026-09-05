package com.helpdesk.profile.controller;

import com.helpdesk.profile.dto.ProfileUpdateRequest;
import com.helpdesk.profile.dto.RegistrationRequest;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * REST endpoints for F1 - Student Profile & Preferences Management.
 * Keep controllers thin: validate the request shape, call the service, return a response.
 * All the actual logic lives in StudentService.
 *
 * A note on security: SecurityConfig only checks that a request is "authenticated"
 * (or, for a few specific paths, that the user has a given role) - it does NOT
 * check WHICH student is making the request vs. WHICH student's data they're
 * touching. That means logging in as Student A and calling
 * GET/PUT/DELETE /api/students/{B's id} would succeed unless we stop it
 * ourselves. That's what the "self-access check" in findById/updateProfile/
 * deactivate below does - see the comment on isOwnProfile() for the full
 * explanation. (findAll is different: it's restricted by ROLE, not by
 * ownership - see the comment on that method instead.)
 */
@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;

    @Autowired
    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    // POST /api/students -> register a brand new student account (US-03).
    // Public endpoint (see SecurityConfig) - you can't log in before your account exists.
    //
    // Takes a RegistrationRequest rather than the Student entity directly (which
    // this endpoint originally did) - see the comment on RegistrationRequest for
    // why: the password complexity rule can only be checked against the raw,
    // not-yet-hashed password, and RegistrationRequest is the only place that
    // value exists before StudentService hashes it.
    @PostMapping
    public ResponseEntity<Student> register(@Valid @RequestBody RegistrationRequest request) {
        Student saved = studentService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // GET /api/students -> list every student in one response, with no filtering.
    //
    // Access rule: restricted to Officers/Admins only, but NOT by any code in this
    // class - it's enforced by SecurityConfig's
    //   .requestMatchers(HttpMethod.GET, "/api/students").hasAnyRole("OFFICER", "ADMIN")
    // rule, which rejects a plain Student's request with 403 Forbidden before it
    // ever reaches this method. It has to be a role check here (not an ownership
    // check like isOwnProfile()) because this endpoint isn't about any single
    // student's own data - it dumps everyone's, which only staff should see.
    @GetMapping
    public List<Student> findAll() {
        return studentService.findAll();
    }

    // GET /api/students/me -> fetch the CURRENTLY LOGGED-IN student's own profile.
    //
    // Why this exists: the frontend (e.g. profile.html) knows who's logged in
    // only via the session cookie - it has no way to know that student's
    // numeric id up front, and guessing/enumerating ids is exactly what
    // isOwnProfile() below exists to prevent. This reuses that same pattern -
    // authentication.getName() is the email the student logged in with (see
    // StudentUserDetailsService), so looking them up by email is the direct
    // equivalent of isOwnProfile()'s email comparison, just without needing an
    // {id} in the URL first. Any authenticated user can call this for
    // themselves; there's no cross-student access risk since the lookup is
    // always tied to whoever the session belongs to, not to caller input.
    //
    // 403, not 404, when no student matches: this only happens when a session
    // authenticated successfully but the row behind it has since disappeared
    // (e.g. the account was deleted after the session was issued). That's the
    // same "doesn't exist" -> "forbidden" choice isOwnProfile() makes below,
    // for the same reason - treating it as a plain 404 here would read like
    // "this endpoint doesn't exist" rather than "your session no longer
    // corresponds to a real account", and would throw if callers ever started
    // assuming a 404 body always means "resource not found" rather than
    // "identity gone".
    @GetMapping("/me")
    public ResponseEntity<Student> getCurrentStudent(Authentication authentication) {
        return studentService.findByEmail(authentication.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    // GET /api/students/{id} -> fetch one student's profile, e.g. for a dashboard view.
    // Returns 404 if no student exists with that id.
    //
    // Access rule: same privacy gap as GET /api/students (see SecurityConfig's
    // comment on that rule) - without a check here, any logged-in Student could
    // view any OTHER student's profile just by changing the {id} in the URL, even
    // though SecurityConfig only guarantees "someone" is logged in. Officers and
    // Admins are allowed to look up any student (that's their job), so only plain
    // Students are restricted to their own profile via isOwnProfile(), the same
    // helper already used to gate updateProfile/deactivate below.
    @GetMapping("/{id}")
    public ResponseEntity<Student> findById(@PathVariable Long id, Authentication authentication) {
        if (!isOfficerOrAdmin(authentication) && !isOwnProfile(id, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return studentService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // PUT /api/students/{id} -> edit your own profile details (US-01).
    // "authentication" is filled in automatically by Spring Security with whoever
    // is logged in for the current request (from the session set up at login).
    //
    // Takes a ProfileUpdateRequest, not the Student entity itself - see the
    // comment on ProfileUpdateRequest for the full reasoning. Briefly: reusing
    // Student here (as this endpoint originally did) meant its @NotBlank
    // password field applied to profile edits too, even though this endpoint
    // never changes the password and GET responses never return one to send
    // back (it's WRITE_ONLY). It also meant email/studentId/role could be
    // included in the request body and would just be silently dropped by
    // the service, which is a confusing API shape. A dedicated request type
    // only exposes the fields this endpoint actually edits.
    @PutMapping("/{id}")
    public ResponseEntity<Student> updateProfile(@PathVariable Long id,
                                                  @Valid @RequestBody ProfileUpdateRequest updatedDetails,
                                                  Authentication authentication) {
        if (!isOwnProfile(id, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(studentService.updateProfile(id, updatedDetails));
    }

    // DELETE /api/students/{id} -> self-service deactivation (US-02).
    // Soft-delete only: see StudentService.deactivate, which flips an "active" flag
    // instead of removing the row from the database.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id, Authentication authentication,
                                            HttpServletRequest request) {
        if (!isOwnProfile(id, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        studentService.deactivate(id);

        // Flipping the "active" flag in the database only blocks FUTURE login
        // attempts (StudentUserDetailsService checks it via .disabled(...),
        // enforced by DaoAuthenticationProvider) - it does nothing to a
        // session that was already authenticated before this call. Spring
        // Security doesn't re-run the UserDetailsService check on every
        // request by default; it trusts whatever Authentication object is
        // already sitting in the session. Without explicitly ending the
        // session here, a student who deactivates their own account while
        // logged in could keep using that same session/cookie normally until
        // it naturally expires, even though they're now "deactivated". This
        // mirrors AuthController.logout()'s invalidate-session-and-clear-
        // context pattern, run immediately as part of deactivation instead of
        // waiting for an explicit logout call.
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        return ResponseEntity.noContent().build();
    }

    /**
     * Self-access check: is the logged-in user the SAME student as the one
     * identified by {@code id}?
     *
     * Why this is needed: Spring Security's login (see AuthController) proves WHO
     * you are and stores that in your session, but SecurityConfig's rule for these
     * endpoints is just ".anyRequest().authenticated()" - it only checks that
     * *someone* is logged in, not that they're logged in as the student whose
     * data they're trying to change. Without this check, any logged-in student
     * could edit or deactivate any other student's profile just by changing the
     * {id} in the URL (this class of bug is called "IDOR" - Insecure Direct
     * Object Reference).
     *
     * How it works: we look up which student owns the account currently logged
     * in (authentication.getName() is the email they logged in with, since
     * StudentUserDetailsService uses email as the username) and compare it to
     * the email of the student stored under {@code id}. They must match.
     *
     * If {@code id} doesn't belong to any student, we deliberately return false
     * (forbidden) rather than treating it differently to a "not your profile"
     * case - this avoids leaking whether a given id exists to someone probing
     * the API.
     */
    private boolean isOwnProfile(Long id, Authentication authentication) {
        return studentService.findById(id)
                .map(student -> student.getEmail().equals(authentication.getName()))
                .orElse(false);
    }

    /**
     * Is the logged-in user an Officer or Admin (as opposed to a plain Student)?
     *
     * Used to let staff bypass the self-access check in findById: Officers and
     * Admins legitimately need to look up any student's profile, so they aren't
     * restricted to "their own" the way a Student is. This reads the same
     * "ROLE_x" authorities that StudentUserDetailsService set up at login (and
     * that SecurityConfig's hasRole(...) rules already rely on elsewhere), so a
     * Student's account - even if they guess/enumerate other ids - can never
     * satisfy this check.
     */
    private boolean isOfficerOrAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_OFFICER")
                        || authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
