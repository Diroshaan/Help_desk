package com.helpdesk.common.reference.dto;

import com.helpdesk.common.reference.entity.Category;

import java.util.List;

/** Category for the API, with the department flattened to code and name for the dropdown. */
public record CategoryResponse(
        Long id,
        String name,
        String departmentCode,
        String departmentName
) {

    // department is LAZY: call this inside a transaction or on a category loaded with JOIN FETCH.
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDepartment().getCode(),
                category.getDepartment().getName()
        );
    }

    public static List<CategoryResponse> fromAll(List<Category> categories) {
        return categories.stream().map(CategoryResponse::from).toList();
    }
}
