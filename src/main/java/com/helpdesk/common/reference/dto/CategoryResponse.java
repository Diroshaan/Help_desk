package com.helpdesk.common.reference.dto;

import com.helpdesk.common.reference.entity.Category;

import java.util.List;

/**
 * SHARED REFERENCE DATA - not owned by any single feature.
 *
 * What a category looks like when it leaves the API.
 *
 * The department is FLATTENED into two plain fields rather than nested as a
 * DepartmentResponse object. That is a deliberate choice about who this
 * response is for: its job is to fill a dropdown and tell the student where
 * their ticket will land. A form that has to reach into
 * category.department.name to print one line of text is harder to write than
 * one that reads category.departmentName, and nesting would send the desk's
 * email and phone number to a screen that has no use for them.
 *
 * If a screen later genuinely needs the full department object alongside the
 * category, the right answer is a second response type for that screen - not to
 * inflate this one until it serves everybody badly.
 */
public record CategoryResponse(
        Long id,
        String name,
        String departmentCode,
        String departmentName
) {

    /**
     * IMPORTANT: only safe to call inside an open transaction, or on a Category
     * loaded by a query that JOIN FETCHed its department.
     *
     * Category.department is LAZY. Reading it outside the persistence context -
     * for example if a controller called this on an entity returned from a
     * plain findAll() after the transaction had closed - throws
     * LazyInitializationException. That is why the conversion happens in
     * ReferenceDataService while its @Transactional method is still running,
     * and why CategoryRepository's finders JOIN FETCH the department rather than
     * leaving it to be resolved later.
     */
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
