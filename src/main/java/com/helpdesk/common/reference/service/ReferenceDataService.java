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
 * Read-only access to departments and categories. Creating and editing them is an admin
 * job and lives in the admin feature.
 */
@Service
public class ReferenceDataService {

    private final DepartmentRepository departmentRepository;
    private final CategoryRepository categoryRepository;

    @Autowired
    public ReferenceDataService(DepartmentRepository departmentRepository,
                                CategoryRepository categoryRepository) {
        this.departmentRepository = departmentRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> allDepartments() {
        return DepartmentResponse.fromAll(departmentRepository.findByActiveTrueOrderByNameAsc());
    }

    // Converted to DTOs inside the transaction because Category.department is LAZY.
    @Transactional(readOnly = true)
    public List<CategoryResponse> allCategories() {
        return CategoryResponse.fromAll(categoryRepository.findSelectableWithDepartment());
    }

    // 404 for an unknown code, so a typo isn't mistaken for a department with no categories.
    @Transactional(readOnly = true)
    public List<CategoryResponse> categoriesFor(String departmentCode) {
        if (!departmentRepository.existsById(departmentCode)) {
            throw new ResourceNotFoundException(
                    "No support department exists with code '" + departmentCode + "'.");
        }
        return CategoryResponse.fromAll(
                categoryRepository.findSelectableByDepartmentCode(departmentCode));
    }

    // Returns the entity (not a DTO) for other services that need to link to it, e.g. a new ticket.
    @Transactional(readOnly = true)
    public Category requireCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No ticket category exists with id " + id + "."));
    }

    @Transactional(readOnly = true)
    public Department requireDepartment(String code) {
        return departmentRepository.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No support department exists with code '" + code + "'."));
    }
}
