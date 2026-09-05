package com.helpdesk.auth;

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

    // Handles actually persisting the authenticated user into the HTTP session,
    // so subsequent requests (with the same session cookie) are recognised as
    // logged in without needing to send the password again.
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @Autowired
    public AuthController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
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
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Logged out");
    }
}
