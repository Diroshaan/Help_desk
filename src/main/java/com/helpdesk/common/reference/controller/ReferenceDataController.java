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
 * Read-only lists for the ticket form: departments, categories, and the categories of one
 * department. Any logged-in user can read them; they are not needed before login.
 */
@RestController
public class ReferenceDataController {

    private final ReferenceDataService referenceDataService;

    @Autowired
    public ReferenceDataController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping("/api/departments")
    public ResponseEntity<List<DepartmentResponse>> departments() {
        return ResponseEntity.ok(referenceDataService.allDepartments());
    }

    @GetMapping("/api/categories")
    public ResponseEntity<List<CategoryResponse>> categories() {
        return ResponseEntity.ok(referenceDataService.allCategories());
    }

    // 404 if the department code doesn't exist.
    @GetMapping("/api/departments/{code}/categories")
    public ResponseEntity<List<CategoryResponse>> categoriesOfDepartment(@PathVariable String code) {
        return ResponseEntity.ok(referenceDataService.categoriesFor(code));
    }
}
