package com.helpdesk.queue.service;

import com.helpdesk.common.reference.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Routes a new ticket to a department based on its category.
 */
@Service
public class RoutingService {

    private final CategoryRepository categoryRepository;

    @Autowired
    public RoutingService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // Each category belongs to one department. Who owns the ticket inside that
    // department is still decided by pick-up or assign.
    @Transactional(readOnly = true)
    public String departmentFor(String categoryName) {
        return categoryRepository.findByNameWithDepartment(categoryName)
                .map(c -> c.getDepartment().getCode()).orElse(null);
    }
}
