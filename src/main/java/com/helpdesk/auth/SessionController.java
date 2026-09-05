package com.helpdesk.auth;

import com.helpdesk.profile.entity.Student;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Answers the question "who is logged in right now?".
 *
 * <p>Every page in the front end needs this before it can do anything else.
 * profile.html needs the student's id to build the PUT and DELETE URLs;
 * welcome.html needs to know whether to offer Register or My profile;
 * delete-account.html needs to show whose account is about to be removed.
 * Without it each page has to assume it is talking to a guest, which is why
 * logging in appeared to do nothing — the profile page bounced straight back
 * to the welcome page.</p>
 *
 * <p>Spring Security already knows the answer: once the login succeeds it
 * stores an {@link Authentication} in the session, and Spring hands it to any
 * controller method that asks for one. All this class does is turn that name
 * back into the Student row behind it.</p>
 *
 * <p>The path is <b>/api/auth/session</b> rather than /api/auth/me so that it
 * cannot collide with an existing mapping in {@code AuthController}. Two
 * methods mapped to the same path make Spring fail at startup with
 * "Ambiguous mapping", and this file was written without being able to read
 * that controller.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class SessionController {

    private final EntityManager entityManager;

    public SessionController(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @GetMapping("/session")
    public ResponseEntity<Student> currentSession(Authentication authentication) {

        // An anonymous visitor still arrives with an Authentication object —
        // Spring's AnonymousAuthenticationToken, whose name is "anonymousUser".
        // Checking isAuthenticated() alone is not enough, because that token
        // reports true.
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // The name Spring holds is whatever StudentUserDetailsService looked the
        // student up by. That is either the email or the Student ID depending on
        // how the student logged in, so both columns are checked.
        Student student = entityManager
                .createQuery(
                        "select s from Student s where s.email = :name or s.studentId = :name",
                        Student.class)
                .setParameter("name", authentication.getName())
                .getResultStream()
                .findFirst()
                .orElse(null);

        if (student == null) {
            // Authenticated against a row that no longer exists — for example
            // the student deleted their own account and the session outlived it.
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Detach before clearing the password. While the entity is still managed,
        // a change to one of its fields can be written back to the database when
        // the persistence context flushes. Detaching first makes this object a
        // plain Java object, so blanking the hash affects only what is sent to
        // the browser and never the stored row.
        entityManager.detach(student);
        student.setPassword(null);

        return ResponseEntity.ok(student);
    }
}
