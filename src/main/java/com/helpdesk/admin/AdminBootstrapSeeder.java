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
 * Creates the first administrator when there is no active one. Without it a fresh
 * database has no way in, because only an admin can create an admin.
 * It checks for an ACTIVE admin, so a system whose only admin was suspended can recover,
 * and it does nothing while one exists. The password comes from
 * helpdesk.bootstrap-admin.password, or is generated and logged once, so no default
 * password sits in the repo.
 * If the bootstrap email is already used: a suspended admin is reactivated with a new
 * password; a removed admin or another kind of account is left alone and an error is
 * logged (the app still starts).
 */
@Component
public class AdminBootstrapSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapSeeder.class);

    private final AdministratorRepository administratorRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    // .local is not a real domain, so nobody mistakes it for a real address.
    @Value("${helpdesk.bootstrap-admin.email:admin@helpdesk.local}")
    private String bootstrapEmail;

    @Value("${helpdesk.bootstrap-admin.display-name:System Administrator}")
    private String bootstrapDisplayName;

    // blank = generate one and print it
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
            log.debug("An active administrator already exists - bootstrap seeder skipped.");
            return;
        }

        // Search all account types, since the email is unique across every one of them.
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
        // No staff number: this account exists before any numbers are issued.
        administratorRepository.save(administrator);

        reportCredentials("FIRST-RUN ADMINISTRATOR CREATED", "one has been created", password, generated);
    }

    // Reactivated with a new password, since the old one may be known to the wrong person.
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
            // Only the hash is stored, so this log line is the one place the password appears.
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
     * SecureRandom, not Random, so the password can't be predicted from the start time.
     * 12 bytes in URL-safe Base64, with "Aa1" in front so it passes the same
     * upper/lower/digit rule as the provisioning forms.
     */
    private String generatePassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return "Aa1" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
