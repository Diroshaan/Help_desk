package com.helpdesk.admin;

import com.helpdesk.common.user.entity.Administrator;
import com.helpdesk.common.user.entity.AppUser;
import com.helpdesk.common.user.repository.AdministratorRepository;
import com.helpdesk.common.user.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * F6 - System Analytics, Provisioning & Announcements
 *
 * Creates the first administrator account on a deployment that has none.
 *
 *
 * WHY THIS IS NOT OPTIONAL
 * ------------------------
 * Everything F6 builds sits under /api/admin/**, which SecurityConfig guards
 * with hasRole("ADMIN"). Provisioning an administrator is itself an
 * administrator action. So on a fresh database the feature is unreachable: no
 * administrator exists, and the only way to create one requires being one
 * already. Without this class the entire feature - every endpoint, the
 * dashboard, the announcements - can be demonstrated to nobody, including the
 * marker.
 *
 * That circularity is the specific problem
 * AdministratorRepository.existsByActiveTrue() was written to solve, and its
 * comment says so. This is the caller it was waiting for.
 *
 *
 * WHY existsByActiveTrue() AND NOT count() == 0
 * ---------------------------------------------
 * The question is not "does an administrator row exist", it is "can anybody
 * administer this system". A deployment whose only administrator has been
 * deactivated is locked out exactly as thoroughly as one that never had an
 * administrator - and count() > 0 would report that everything is fine while
 * nobody can log in. Filtering on active is what makes this a working recovery
 * path rather than a one-time convenience.
 *
 * It also means the seeder cannot be used to slip a spare admin into a healthy
 * system: while one active administrator exists, this does nothing at all.
 *
 *
 * WHY THE PASSWORD IS GENERATED AND LOGGED, NOT HARDCODED
 * -------------------------------------------------------
 * The obvious version of this class ships with "admin"/"admin123" in the source
 * - which means the credentials of every deployment of this application are
 * published in a public Git repository, forever, and stay valid until somebody
 * remembers to change them. That is not a hypothetical: it is the single most
 * common way a student project gets marked down on security.
 *
 * Instead: the password comes from configuration if one is supplied, and
 * otherwise is a fresh random value printed to the startup log exactly once, on
 * the run that creates the account. A log line is visible to whoever is starting
 * the application - which is the person who should have it - and it is never
 * valid for any other deployment. To choose the password instead, run with
 *
 *     -Dhelpdesk.bootstrap-admin.password=YourPassw0rd
 *
 * or add helpdesk.bootstrap-admin.* to an application properties file. Note
 * that reading it from configuration rather than writing it into
 * application.properties is also what keeps this out of a shared file that F6
 * does not own.
 *
 *
 * WHY ApplicationRunner, AND WHY IT MIRRORS ReferenceDataSeeder
 * ------------------------------------------------------------
 * @PostConstruct runs while the context is still being built, which is too early
 * to rely on the schema existing and turns any failure into a bean creation
 * error. ApplicationRunner runs once, after the context is up and Hibernate has
 * created the tables. Idempotent on every restart, for the same reason and by
 * the same means as ReferenceDataSeeder: while an active administrator exists it
 * does nothing, so restarting twenty times produces one administrator and a
 * password changed afterwards is not quietly reverted on the next boot.
 *
 *
 * WHEN THE BOOTSTRAP ACCOUNT ALREADY EXISTS
 * -----------------------------------------
 * "No ACTIVE administrator" does not mean "no admin@helpdesk.local row". On a
 * database that has run before, the bootstrap account is usually still there,
 * suspended. Inserting it again would hit the unique email constraint and stop
 * the application from starting - on exactly the deployment that most needs it
 * to start. So the seeder looks the email up first:
 *
 *   missing                 -> insert it, as on a fresh database
 *   a SUSPENDED administrator -> reactivate it with a new password
 *   a REMOVED administrator   -> leave it alone and log an ERROR. Removal is
 *                              final; quietly undoing it at startup would turn
 *                              the seeder into a back door around that rule.
 *   anybody else's account    -> leave it alone and log an ERROR; the address
 *                              belongs to a student or officer.
 *
 * In the last two cases the application still starts, because a locked-out
 * admin panel is a problem an operator can fix, and a crash loop is not.
 */
@Component
public class AdminBootstrapSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapSeeder.class);

    private final AdministratorRepository administratorRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Defaults that make a fresh clone runnable with no configuration at all.
     *
     * The .local address is not a real domain and cannot receive mail, which is
     * the point: a placeholder that looks like a working university address is
     * one copy-paste away from somebody believing it is one.
     */
    @Value("${helpdesk.bootstrap-admin.email:admin@helpdesk.local}")
    private String bootstrapEmail;

    @Value("${helpdesk.bootstrap-admin.display-name:System Administrator}")
    private String bootstrapDisplayName;

    /** Blank means "generate one and print it". See the class comment. */
    @Value("${helpdesk.bootstrap-admin.password:}")
    private String bootstrapPassword;

    @Autowired
    public AdminBootstrapSeeder(AdministratorRepository administratorRepository,
                                AppUserRepository appUserRepository,
                                PasswordEncoder passwordEncoder) {
        this.administratorRepository = administratorRepository;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (administratorRepository.existsByActiveTrue()) {
            // Nothing to do, and nothing to say beyond debug level. An INFO line
            // on every restart of a healthy system is noise that trains people
            // to stop reading the startup log, which is where the line that
            // DOES matter is printed.
            log.debug("An active administrator already exists - bootstrap seeder skipped.");
            return;
        }

        // AppUserRepository, not AdministratorRepository, for the lookup: the
        // email may belong to any kind of account, and the unique constraint
        // that would otherwise crash startup spans all of them.
        Optional<AppUser> existing = appUserRepository.findByEmail(bootstrapEmail);

        if (existing.isEmpty()) {
            createBootstrapAdministrator();
        } else if (existing.get() instanceof Administrator administrator && !administrator.isRemoved()) {
            reactivate(administrator);
        } else {
            log.error("No active administrator exists, but {} already belongs to an account this "
                    + "seeder will not reuse ({}), so no administrator was created. It only "
                    + "reactivates a suspended administrator: it never undoes a removal or takes "
                    + "over another kind of account. To recover, set "
                    + "helpdesk.bootstrap-admin.email to an unused address and restart, or fix "
                    + "the users table by hand.",
                    bootstrapEmail, describe(existing.get()));
        }
    }

    private void createBootstrapAdministrator() {
        boolean generated = bootstrapPassword == null || bootstrapPassword.isBlank();
        String password = generated ? generatePassword() : bootstrapPassword;

        Administrator administrator = new Administrator(
                bootstrapEmail,
                passwordEncoder.encode(password),
                bootstrapDisplayName
        );
        // staffNumber deliberately left null: the first administrator is a
        // technical bootstrap account created before anybody has been issued a
        // number. Administrator.staffNumber is nullable for exactly this case,
        // and inventing a fake number would be worse than an empty column.
        administratorRepository.save(administrator);

        reportCredentials("FIRST-RUN ADMINISTRATOR CREATED", "one has been created", password, generated);
    }

    /**
     * A suspended bootstrap account comes back with a NEW password. The old hash
     * may belong to somebody who no longer should have it, and the operator
     * reading the startup log needs a value they actually know.
     */
    private void reactivate(Administrator administrator) {
        boolean generated = bootstrapPassword == null || bootstrapPassword.isBlank();
        String password = generated ? generatePassword() : bootstrapPassword;

        administrator.setPassword(passwordEncoder.encode(password));
        administrator.setActive(true);
        administratorRepository.save(administrator);

        reportCredentials("BOOTSTRAP ADMINISTRATOR REACTIVATED",
                "the suspended bootstrap account has been reactivated", password, generated);
    }

    private void reportCredentials(String headline, String what, String password, boolean generated) {
        if (generated) {
            // Printed once, on the run that creates the account, and never
            // again. The hash is what is stored; this value exists only in this
            // log line and in the operator's memory.
            log.warn("""

                    ================= {} =================
                     No active administrator existed, so {}.
                       email    : {}
                       password : {}
                     This password was generated for this run and is not stored
                     anywhere in readable form. Sign in and change it, or set
                     helpdesk.bootstrap-admin.password to choose your own.
                    ===================================================================
                    """, headline, what, bootstrapEmail, password);
        } else {
            log.warn("{} for {} using the configured bootstrap password.", headline, bootstrapEmail);
        }
    }

    private String describe(AppUser user) {
        return user instanceof Administrator ? "removed administrator" : user.getRole().name().toLowerCase() + " account";
    }

    /**
     * A random password for this deployment.
     *
     * SecureRandom rather than Random: java.util.Random is a linear congruential
     * generator seeded from the clock, so its output is predictable to anybody
     * who knows roughly when the application started - which is not a property
     * a credential should have. SecureRandom draws from the operating system's
     * entropy source.
     *
     * URL-safe Base64 of 12 random bytes gives 96 bits of entropy in 16
     * characters, and no character that a terminal, a shell or a JSON body will
     * mangle. It is then padded with "Aa1" so the value always satisfies the
     * upper/lower/digit rule the provisioning DTOs enforce - otherwise a
     * generated password could be one this application would refuse to accept
     * if the same account were created through the API, which is a confusing
     * inconsistency to leave lying around.
     */
    private String generatePassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return "Aa1" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
