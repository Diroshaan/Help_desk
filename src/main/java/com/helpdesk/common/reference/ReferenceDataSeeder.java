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
 * Seeds the starting departments and categories on startup. Done in Java rather than
 * data.sql so it runs after Hibernate creates the tables and works the same on H2 and MySQL.
 * Only inserts missing rows and never updates existing ones, so restarts are safe and
 * admin changes are kept.
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

    private static final List<Department> DEPARTMENTS = List.of(
            new Department("IT",   "IT Services",           "itdesk@university.lk",    "+94 11 000 0001"),
            new Department("REG",  "Registration",          "registrar@university.lk", "+94 11 000 0002"),
            new Department("FIN",  "Financial Aid",         "finaid@university.lk",    "+94 11 000 0003"),
            new Department("LIB",  "Library",               "library@university.lk",   "+94 11 000 0004"),
            new Department("HOS",  "Hostel & Facilities",   "hostel@university.lk",    "+94 11 000 0005"),
            new Department("EXAM", "Examinations",          "exams@university.lk",     "+94 11 000 0006")
    );

    // Category name -> department code. LinkedHashMap keeps the insert order (and ids) stable.
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

    // One transaction, so we never end up with categories but only some of their departments.
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

            // A bad department code here is a typo in this file, so fail at startup.
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
