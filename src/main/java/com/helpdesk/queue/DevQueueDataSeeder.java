package com.helpdesk.queue;

import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.repository.StudentRepository;
import com.helpdesk.queue.entity.Department;
import com.helpdesk.queue.entity.Officer;
import com.helpdesk.queue.repository.DepartmentRepository;
import com.helpdesk.queue.repository.OfficerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * F4 - Ticket Resolution & Queue Engine (Weerabaddana)
 *
 * Dev-only convenience seeder. H2 is in-memory
 * (spring.datasource.url=jdbc:h2:mem:helpdeskdb, ddl-auto=update), so every
 * restart starts from an empty database, and there is no provisioning
 * endpoint yet to create an Officer (that's F6). Without this, nobody could
 * log in as an officer to exercise /api/queue/** locally at all - there'd
 * be no OFFICER-role account to authenticate as, and no Department for its
 * Officer row to point at.
 *
 * Guarded two ways: @Profile("!test") so a future test profile never runs
 * this (tests should set up their own fixtures, not depend on dev seed
 * data), and the departmentRepository.count() check below so it's a no-op
 * if seed data is already present - defensive in case ddl-auto ever stops
 * being "update" against a throwaway in-memory database and starts
 * pointing at something persistent.
 */
@Component
@Profile("!test")
public class DevQueueDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevQueueDataSeeder.class);

    private static final String SEEDED_DEPARTMENT_NAME = "IT Support";
    // Matches Student.studentId's @Pattern (two letters, then eight digits).
    private static final String SEEDED_OFFICER_STUDENT_ID = "OF20250001";
    private static final String SEEDED_OFFICER_EMAIL = "officer.demo@helpdesk.local";
    private static final String SEEDED_OFFICER_PASSWORD = "Officer@123";

    private final DepartmentRepository departmentRepository;
    private final OfficerRepository officerRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;

    public DevQueueDataSeeder(DepartmentRepository departmentRepository, OfficerRepository officerRepository,
                               StudentRepository studentRepository, PasswordEncoder passwordEncoder) {
        this.departmentRepository = departmentRepository;
        this.officerRepository = officerRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (departmentRepository.count() > 0) {
            return;
        }

        Department department = new Department();
        department.setName(SEEDED_DEPARTMENT_NAME);
        department.setDescription("Seeded for local development - see DevQueueDataSeeder");
        department = departmentRepository.save(department);

        // The officer's "account" is a Student row with role=OFFICER - see
        // queue.entity.Officer's class comment for why there's no separate
        // account table yet. Never store the raw password - passwordEncoder
        // is the same BCrypt bean StudentService uses for real registrations.
        Student officerAccount = new Student();
        officerAccount.setStudentId(SEEDED_OFFICER_STUDENT_ID);
        officerAccount.setFullName("Demo Officer");
        officerAccount.setEmail(SEEDED_OFFICER_EMAIL);
        officerAccount.setPassword(passwordEncoder.encode(SEEDED_OFFICER_PASSWORD));
        officerAccount.setRole("OFFICER");
        officerAccount.setDepartment(SEEDED_DEPARTMENT_NAME);
        officerAccount = studentRepository.save(officerAccount);

        // Officer.id is NOT auto-generated - it must equal the account's id
        // (see queue.entity.Officer).
        Officer officer = new Officer();
        officer.setId(officerAccount.getId());
        officer.setDepartmentId(department.getId());
        officer.setActive(true);
        officerRepository.save(officer);

        log.info("[DevQueueDataSeeder] Seeded dev officer login -> email: {}  password: {}  department: {}",
                SEEDED_OFFICER_EMAIL, SEEDED_OFFICER_PASSWORD, SEEDED_DEPARTMENT_NAME);
    }
}
