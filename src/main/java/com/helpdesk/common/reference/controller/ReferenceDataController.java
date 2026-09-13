package com.helpdesk.common.reference.controller;

import com.helpdesk.common.reference.dto.CategoryResponse;
import com.helpdesk.common.reference.dto.DepartmentResponse;
import com.helpdesk.common.reference.service.ReferenceDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SHARED REFERENCE DATA - not owned by any single feature.
 *
 * Read-only endpoints that hand the frontend the lists it needs to build a
 * "raise a ticket" form:
 *
 *   GET /api/departments                     -> every active support desk
 *   GET /api/categories                      -> every active category, flat
 *   GET /api/departments/{code}/categories   -> the categories of one desk
 *
 *
 * WHY THE THIRD URL IS SHAPED THAT WAY
 * ------------------------------------
 * /api/departments/{code}/categories rather than
 * /api/categories?department={code}. The categories of a department are a
 * sub-collection of that department - the path expresses the containment, and
 * a request for a department that does not exist can honestly answer 404,
 * because the thing in the path really is missing. A query parameter describes
 * a filter applied to the whole collection, where "no such department" is
 * naturally an empty result rather than a missing resource.
 *
 *
 * WHY THERE IS NO @RequestMapping ON THE CLASS
 * --------------------------------------------
 * The three paths do not share a prefix - two start /api/departments and one
 * starts /api/categories - so there is no common root to factor out. Forcing
 * one would mean bending a URL into a shape that misdescribes what it returns.
 *
 *
 * ACCESS CONTROL
 * --------------
 * These paths are not listed in SecurityConfig, so they fall through to
 * .anyRequest().authenticated() - any logged-in user may read them, no
 * specific role required. That is the correct level: the list of support desks
 * is not secret, but it is also not needed before login (the registration form
 * asks for the student's FACULTY, which is a different list entirely), so there
 * is no reason to open it to anonymous callers. Deliberately NOT added to the
 * permitAll block - every path put there is one more thing to justify.
 */
@RestController
public class ReferenceDataController {

    private final ReferenceDataService referenceDataService;

    @Autowired
    public ReferenceDataController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    /**
     * Every support desk a ticket can be routed to.
     *
     * Returns 200 with an empty array if there are none, rather than 404. An
     * empty collection is a truthful answer to "list the departments" - the
     * collection exists and happens to have nothing in it. 404 would mean the
     * endpoint itself is not there, which is a different problem and would send
     * a frontend down an error path when the honest response is "nothing yet".
     */
    @GetMapping("/api/departments")
    public ResponseEntity<List<DepartmentResponse>> departments() {
        return ResponseEntity.ok(referenceDataService.allDepartments());
    }

    /**
     * Every category across every department, each carrying its department's
     * code and name so a single flat dropdown can show "Module registration
     * (Registration)" without a second request.
     */
    @GetMapping("/api/categories")
    public ResponseEntity<List<CategoryResponse>> categories() {
        return ResponseEntity.ok(referenceDataService.allCategories());
    }

    /**
     * The categories of one department, for a form where choosing a desk
     * narrows the subject list.
     *
     * Unlike the two endpoints above, this one CAN 404 - and does, via
     * ResourceNotFoundException from the service, when the code in the path
     * matches no department. See ReferenceDataService.categoriesFor() for why
     * that is worth distinguishing from an empty list.
     */
    @GetMapping("/api/departments/{code}/categories")
    public ResponseEntity<List<CategoryResponse>> categoriesOfDepartment(@PathVariable String code) {
        return ResponseEntity.ok(referenceDataService.categoriesFor(code));
    }
}
