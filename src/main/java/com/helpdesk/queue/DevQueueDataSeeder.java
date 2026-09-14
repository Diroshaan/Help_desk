package com.helpdesk.queue;

import com.helpdesk.common.reference.entity.Department;
import com.helpdesk.common.reference.repository.DepartmentRepository;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.OfficerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * F4 - Ticket Resolution & Queue Engine (Weerabaddana)
 *
 * Dev-only convenience seeder. H2 is in-memory
 * (spring.datasource.url=jdbc:h2:mem:helpdeskdb, ddl-auto=update), so every
 * restart starts from an empty database, and there is no provisioning
 * endpoint yet to create an Officer (that's F6). Without this, nobody could
 * log in as an officer to exercise /api/queue/** locally at all.
 *
 * Officer is now the shared common.user.entity.Officer (a real account row
 * in "officers", joined to "users" - see that class's javadoc), not a
 * Student row with a role string, so this seeder creates one directly
 * through OfficerRepository instead of going through StudentRepository.
 *
 * The department it attaches the officer to is looked up rather than always
 * created: common.reference.ReferenceDataSeeder seeds the real department
 * set (including "IT") on every startup too, and the order the two
 * ApplicationRunner/CommandLineRunner beans run in is not guaranteed. If
 * "IT" is already there, use it; otherwise create it here so this seeder
 * still works standalone.
 *
 * Guarded by officerRepository.existsByStaffNumber(...) rather than a count
 * check, so it's a no-op on any restart that already has this specific demo
 * officer, regardless of what else has been seeded.
 */
@Component
@Profile("!test")
public class DevQueueDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevQueueDataSeeder.class);

    private static final String SEEDED_DEPARTMENT_CODE = "IT";
    private static final String SEEDED_DEPARTMENT_NAME = "IT Services";
    private static final String SEEDED_OFFICER_STAFF_NUMBER = "OF20250001";
    private static final String SEEDED_OFFICER_JOB_TITLE = "Support Officer";
    private static final String SEEDED_OFFICER_EMAIL = "officer.demo@helpdesk.local";
    private static final String SEEDED_OFFICER_PASSWORD = "Officer@123";

    private final DepartmentRepository departmentRepository;
    private final OfficerRepository officerRepository;
    private final PasswordEncoder passwordEncoder;

    public DevQueueDataSeeder(DepartmentRepository departmentRepository, OfficerRepository officerRepository,
                               PasswordEncoder passwordEncoder) {
        this.departmentRepository = departmentRepository;
        this.officerRepository = officerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (officerRepository.existsByStaffNumber(SEEDED_OFFICER_STAFF_NUMBER)) {
            return;
        }

        Department department = departmentRepository.findById(SEEDED_DEPARTMENT_CODE)
                .orElseGet(() -> departmentRepository.save(
                        new Department(SEEDED_DEPARTMENT_CODE, SEEDED_DEPARTMENT_NAME, null, null)));

        // Never store the raw password - passwordEncoder is the same BCrypt
        // bean every real registration path uses.
        Officer officer = new Officer(SEEDED_OFFICER_EMAIL, passwordEncoder.encode(SEEDED_OFFICER_PASSWORD),
                SEEDED_OFFICER_STAFF_NUMBER, SEEDED_OFFICER_JOB_TITLE);
        officer.setDepartments(Set.of(department));
        officerRepository.save(officer);

        log.info("[DevQueueDataSeeder] Seeded dev officer login -> email: {}  password: {}  department: {}",
                SEEDED_OFFICER_EMAIL, SEEDED_OFFICER_PASSWORD, department.getName());
    }
}
