package com.helpdesk.auth;

import com.helpdesk.common.user.entity.AppUser;
import com.helpdesk.common.user.repository.AppUserRepository;
import com.helpdesk.profile.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * F7 - Authentication (shared/cross-cutting).
 *
 * Bridges this project's account entities to Spring Security's authentication
 * system. Spring Security does not know what a Student or an Officer is; this
 * class translates any of them into the generic UserDetails shape it expects,
 * so it can check a password and know what authority the account carries.
 *
 * Because this is the only UserDetailsService bean in the application, Spring
 * Boot wires it into the authentication process automatically - nothing else
 * references this class.
 *
 *
 * WHAT CHANGED WITH THE USER SUPERTYPE
 * ------------------------------------
 * This used to look accounts up through StudentRepository, which searched the
 * students table and only the students table. That was fine while students were
 * the only accounts that existed, and it quietly became a real bug the moment
 * SecurityConfig started referring to ROLE_OFFICER and ROLE_ADMIN: those rules
 * were correct, they guarded the right paths, and no authenticated user could
 * ever satisfy them, because an officer could not log in at all.
 *
 * It now looks up AppUser, the root of the JOINED hierarchy, so one code path
 * authenticates a student, an officer or an administrator. Hibernate returns
 * whichever concrete type the row actually is, and getRole() answers correctly
 * for each without this class testing the type or knowing the subclasses exist.
 * Adding a fourth account type would need no change here at all.
 *
 * The class name is now slightly wrong - it handles more than students - but
 * renaming it is a rename across the security configuration's bean wiring and
 * is deliberately left for its own change rather than bundled into a schema
 * migration.
 */
@Service
public class StudentUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    /**
     * Kept only for the Student-ID login path below.
     *
     * A student may type either their email or their registration number, and
     * the registration number exists only on the students table - it is not a
     * property of AppUser, because officers and administrators do not have one.
     * So that one lookup has to go through the student repository specifically.
     */
    private final StudentRepository studentRepository;

    @Autowired
    public StudentUserDetailsService(AppUserRepository appUserRepository,
                                     StudentRepository studentRepository) {
        this.appUserRepository = appUserRepository;
        this.studentRepository = studentRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        // Email first, across every account type; then Student ID as a fallback.
        //
        // The order matters and is not arbitrary. Email is the only identifier
        // every account type has, so trying it first means officers and
        // administrators resolve on the first query and never touch the second.
        // A student typing their registration number falls through to the
        // second lookup and costs one extra query, which is the cheaper case to
        // penalise because it is the less common one.
        //
        // Optional.or() takes a Supplier, so the second lookup does not run
        // unless the first came back empty - this is two queries only in the
        // fallback case, not always.
        AppUser user = appUserRepository.findByEmail(login)
                .or(() -> studentRepository.findByStudentId(login))
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found for '" + login + "'"));

        // The principal name is always the EMAIL, even when the person logged in
        // with a Student ID.
        //
        // This is load-bearing, not tidiness. Every ownership check in the
        // application compares Authentication.getName() against an account's
        // email - StudentController.isOwnProfile() and
        // BookmarkFolderController.currentStudentId() both do exactly that. If
        // the principal were sometimes an email and sometimes a registration
        // number, those checks would silently fail for anyone who logged in the
        // second way, and they would fail CLOSED: the student would be refused
        // access to their own profile, with a 403 and no explanation.
        //
        // Normalising here means the rest of the application can rely on one
        // answer to "who is this?" regardless of how they signed in.
        return User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                // "ROLE_" is a Spring Security convention: hasRole("OFFICER")
                // checks internally for an authority literally named
                // "ROLE_OFFICER". getRole() returns the Role enum, so name()
                // produces STUDENT / OFFICER / ADMIN and the concatenation can
                // no longer produce "ROLE_null" or a space-padded authority that
                // matches nothing - both of which were possible while role was
                // an unvalidated String column.
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                // A deactivated account fails authentication with
                // DisabledException even when the password is correct.
                // AuthController catches that specifically and answers 401 with
                // "This account has been deactivated" rather than the generic
                // bad-credentials message, so someone who closed their own
                // account is told what actually happened.
                .disabled(!user.isActive())
                .build();
    }
}
