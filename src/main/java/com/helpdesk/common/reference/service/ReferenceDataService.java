package com.helpdesk.common.reference.service;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.reference.dto.CategoryResponse;
import com.helpdesk.common.reference.dto.DepartmentResponse;
import com.helpdesk.common.reference.entity.Category;
import com.helpdesk.common.reference.entity.Department;
import com.helpdesk.common.reference.repository.CategoryRepository;
import com.helpdesk.common.reference.repository.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SHARED REFERENCE DATA - not owned by any single feature.
 *
 * Read access to the department and category lookup tables.
 *
 *
 * WHY THERE ARE NO WRITE METHODS HERE
 * -----------------------------------
 * Creating, renaming and retiring departments and categories is an
 * ADMINISTRATIVE action - "administrators must be able to provision..." in the
 * requirement specification - and administration is F6. Adding write methods
 * here would be building part of someone else's feature inside a shared
 * package, which is exactly the coupling this package exists to avoid. This
 * service does the one thing every feature needs (read the list) and stops.
 * F6 adds an AdminReferenceService alongside it when that sprint comes.
 *
 * Seeding the initial rows is not a counter-example: that is a one-off
 * bootstrap, it lives in ReferenceDataSeeder, and it writes through the
 * repository directly rather than pretending to be a service operation.
 */
@Service
public class ReferenceDataService {

    private final DepartmentRepository departmentRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Constructor injection, matching StudentService.
     *
     * Field injection with @Autowired on the fields would be shorter, but it
     * makes the dependencies invisible from outside the class and impossible to
     * supply in a plain unit test without reflection. Taking them as
     * constructor arguments means an object that exists is an object that is
     * fully wired, and the fields can be final.
     */
    @Autowired
    public ReferenceDataService(DepartmentRepository departmentRepository,
                                CategoryRepository categoryRepository) {
        this.departmentRepository = departmentRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Every department a student may send a ticket to.
     *
     * readOnly = true is not decoration. It tells Hibernate not to take
     * snapshots of the loaded entities for dirty checking, since nothing in a
     * read-only transaction can have changed - less memory and no pointless
     * comparison at flush time. It also lets the JDBC driver mark the
     * connection read-only, which some databases use to route the query
     * differently. Most usefully for correctness, it means an accidental
     * setter call in this method cannot silently reach the database.
     */
    @Transactional(readOnly = true)
    public List<DepartmentResponse> allDepartments() {
        return DepartmentResponse.fromAll(departmentRepository.findByActiveTrueOrderByNameAsc());
    }

    /**
     * Every category a student may file a ticket under, across all departments.
     *
     * The conversion to DTOs happens HERE, inside the transaction, on purpose.
     * Category.department is lazily loaded; if this method returned entities and
     * let the controller convert them, the transaction would already have closed
     * and reading the department would throw LazyInitializationException. A
     * service that returns DTOs rather than entities makes that class of bug
     * impossible rather than merely unlikely - the persistence context never
     * escapes this layer.
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> allCategories() {
        return CategoryResponse.fromAll(categoryRepository.findSelectableWithDepartment());
    }

    /**
     * The categories belonging to one department, for a cascading form where
     * picking a desk narrows the subject list.
     *
     * The existence check is deliberate and comes first. Without it, asking for
     * the categories of a department that does not exist returns an empty list
     * with 200 OK - indistinguishable from a real department that happens to
     * have no categories yet. The caller cannot tell "you asked for something
     * that is not there" from "there is nothing to show", so a typo in a
     * department code shows the student an empty dropdown and no explanation.
     * Failing with a 404 (ResourceNotFoundException is mapped to 404 by
     * GlobalExceptionHandler) separates the two cases.
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> categoriesFor(String departmentCode) {
        if (!departmentRepository.existsById(departmentCode)) {
            throw new ResourceNotFoundException(
                    "No support department exists with code '" + departmentCode + "'.");
        }
        return CategoryResponse.fromAll(
                categoryRepository.findSelectableByDepartmentCode(departmentCode));
    }

    /**
     * Loads a Category entity by id, for other services that need to attach one
     * to a record they are saving - F2 setting the category on a new ticket,
     * for example.
     *
     * Returns the ENTITY rather than a DTO, which is the one exception to the
     * rule above, because the caller needs the managed object to store as a
     * foreign key reference, not a copy of its values. The exception is safe
     * because the caller is another service inside its own transaction, not a
     * controller.
     *
     * This is what lets the database-level foreign key actually be used: a
     * caller that has been handed a real, existing Category cannot construct a
     * ticket pointing at a category that was deleted a moment ago.
     */
    @Transactional(readOnly = true)
    public Category requireCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No ticket category exists with id " + id + "."));
    }

    /**
     * The same, for departments. Used by F4 when an officer's queue is
     * identified by department code.
     */
    @Transactional(readOnly = true)
    public Department requireDepartment(String code) {
        return departmentRepository.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No support department exists with code '" + code + "'."));
    }
}
