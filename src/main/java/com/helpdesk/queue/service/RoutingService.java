package com.helpdesk.queue.service;

import com.helpdesk.common.reference.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * F4 - category-based routing (WBHD-25).
 */
@Service
public class RoutingService {

    private final CategoryRepository categoryRepository;

    @Autowired
    public RoutingService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // Category-based routing (Requirement Spec, System Limitations): every category belongs
    // to exactly one department, so a ticket goes to that department's queue on arrival.
    // Ownership WITHIN the department stays manual (pick-up / assign).
    @Transactional(readOnly = true)
    public String departmentFor(String categoryName) {
        return categoryRepository.findByNameWithDepartment(categoryName)
                .map(c -> c.getDepartment().getCode()).orElse(null);
    }
}
