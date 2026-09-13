package com.helpdesk.common.reference;

import com.helpdesk.common.reference.entity.Category;
import com.helpdesk.common.reference.entity.Department;
import com.helpdesk.common.reference.repository.CategoryRepository;
import com.helpdesk.common.reference.repository.DepartmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SHARED REFERENCE DATA - not owned by any single feature.
 *
 * Puts the starting set of departments and categories into the database the
 * first time the application runs against an empty schema.
 *
 *
 * WHY THIS IS JAVA AND NOT data.sql
 * ---------------------------------
 * Spring Boot will happily run a data.sql on startup, and that is the usual
 * answer. It is the wrong one here for two reasons.
 *
 * The first is ordering. With spring.jpa.hibernate.ddl-auto=update, Spring runs
 * data.sql BEFORE Hibernate has created the tables, so every INSERT fails
 * against a table that does not exist yet. Fixing that needs
 * spring.jpa.defer-datasource-initialization=true - a non-obvious property
 * whose absence produces a confusing startup failure for the next person who
 * clones the repository.
 *
 * The second is that the project runs on two different databases. H2 by default
 * and MySQL under the mysql profile accept slightly different SQL, so a single
 * data.sql either has to stay in a dialect-neutral subset or be duplicated per
 * profile. Going through the repositories means Hibernate writes whichever
 * dialect is in use, and the seed data is identical on both by construction -
 * which matters, because a teammate developing on H2 and this application
 * running on MySQL must see the same category list or their ticket ids will not
 * line up with anyone else's.
 *
 *
 * WHY ApplicationRunner AND NOT @PostConstruct
 * --------------------------------------------
 * @PostConstruct on a bean runs while the application context is still being
 * built, which is too early to depend on the schema being ready and makes any
 * failure surface as a bean creation error. ApplicationRunner runs once, after
 * the context is fully started and the tables exist.
 *
 *
 * IDEMPOTENCE
 * -----------
 * This runs on EVERY startup, so it must be safe to run repeatedly. Each row is
 * inserted only if it is absent. Restarting the application twenty times leaves
 * exactly one copy of each department and category.
 *
 * Just as important is what it does NOT do: it never updates or deletes a row
 * that already exists. If an administrator renames a category or retires a
 * department, the next restart leaves that change alone rather than quietly
 * reverting it. Seed data is a starting point, not a definition the application
 * re-imposes on the live database every time it boots.
 */
@Component
public class ReferenceDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataSeeder.class);

    private final DepartmentRepository departmentRepository;
    private final CategoryRepository categoryRepository;

    @Autowired
    public ReferenceDataSeeder(DepartmentRepository departmentRepository,
                               CategoryRepository categoryRepository) {
        this.departmentRepository = departmentRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * The six support desks.
     *
     * The first three, and their contact details, are copied from the DESKS
     * array hardcoded in Welcome.jsx - the landing page footer has been showing
     * these all along. Moving them into the database is the whole point: the
     * footer becomes a rendering of real data rather than a list that has to be
     * kept in step with the backend by hand.
     */
    private static final List<Department> DEPARTMENTS = List.of(
            new Department("IT",   "IT Services",           "itdesk@university.lk",    "+94 11 000 0001"),
            new Department("REG",  "Registration",          "registrar@university.lk", "+94 11 000 0002"),
            new Department("FIN",  "Financial Aid",         "finaid@university.lk",    "+94 11 000 0003"),
            new Department("LIB",  "Library",               "library@university.lk",   "+94 11 000 0004"),
            new Department("HOS",  "Hostel & Facilities",   "hostel@university.lk",    "+94 11 000 0005"),
            new Department("EXAM", "Examinations",          "exams@university.lk",     "+94 11 000 0006")
    );

    /**
     * Category name -> owning department code.
     *
     * These are drawn from the TOPICS array on the landing page, which is the
     * closest thing the project has to evidence of what students actually ask
     * about. Using the same subjects means the "popular help topics" chips and
     * the ticket form agree with each other instead of offering two unrelated
     * vocabularies for the same problems.
     *
     * A LinkedHashMap-backed ordered Map rather than a plain Map.of() so the
     * insertion order is stable, which keeps the startup log readable and makes
     * the seeded ids deterministic across a fresh database on any machine.
     */
    private static final Map<String, String> CATEGORIES = new LinkedHashMap<>();
    static {
        CATEGORIES.put("Password & account access",   "IT");
        CATEGORIES.put("Network & Wi-Fi (eduroam)",   "IT");
        CATEGORIES.put("Learning management system",  "IT");
        CATEGORIES.put("Module registration",         "REG");
        CATEGORIES.put("Transcripts & certificates",  "REG");
        CATEGORIES.put("Student ID card",             "REG");
        CATEGORIES.put("Fee payment",                 "FIN");
        CATEGORIES.put("Scholarships & financial aid", "FIN");
        CATEGORIES.put("Library loans & fines",       "LIB");
        CATEGORIES.put("Hostel maintenance",          "HOS");
        CATEGORIES.put("Campus facilities",           "HOS");
        CATEGORIES.put("Exam re-sit application",     "EXAM");
        CATEGORIES.put("Results & re-scrutiny",       "EXAM");
    }

    /**
     * One transaction for the whole seed.
     *
     * Departments must be committed before categories can reference them - the
     * foreign key on categories.department_code will reject a category whose
     * department is not there. Running both inside a single transaction means
     * either the whole reference set lands or none of it does, so the
     * application can never start with categories pointing into a half-built
     * department table.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int newDepartments = seedDepartments();
        int newCategories = seedCategories();

        if (newDepartments == 0 && newCategories == 0) {
            log.info("Reference data already present - {} departments, {} categories.",
                    departmentRepository.count(), categoryRepository.count());
        } else {
            log.info("Seeded reference data: {} new department(s), {} new category(ies).",
                    newDepartments, newCategories);
        }
    }

    private int seedDepartments() {
        int inserted = 0;
        for (Department department : DEPARTMENTS) {
            // existsById rather than findById: this only needs a yes/no, and
            // existsById asks the database for a count instead of transferring
            // and constructing an entity that would be thrown away.
            if (!departmentRepository.existsById(department.getCode())) {
                departmentRepository.save(department);
                inserted++;
            }
        }
        return inserted;
    }

    private int seedCategories() {
        int inserted = 0;
        for (Map.Entry<String, String> entry : CATEGORIES.entrySet()) {
            String name = entry.getKey();
            String departmentCode = entry.getValue();

            if (categoryRepository.existsByName(name)) {
                continue;
            }

            // orElseThrow, not a silent skip. If a category names a department
            // code that is not in the DEPARTMENTS list above, that is a typo in
            // this file, and failing loudly at startup is the fastest way to
            // find it. Skipping quietly would leave the application running
            // with a category missing and no indication why.
            Department department = departmentRepository.findById(departmentCode)
                    .orElseThrow(() -> new IllegalStateException(
                            "Seed data error: category '" + name + "' references department '"
                                    + departmentCode + "', which is not defined in DEPARTMENTS."));

            categoryRepository.save(new Category(name, department));
            inserted++;
        }
        return inserted;
    }
}
