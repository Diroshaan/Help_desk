package com.helpdesk.common.reference.repository;

import com.helpdesk.common.reference.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * SHARED REFERENCE DATA - not owned by any single feature.
 *
 * Data access for ticket categories.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Every selectable category, each with its department already loaded.
     *
     * WHY THIS IS A WRITTEN QUERY AND NOT A DERIVED ONE
     * -------------------------------------------------
     * The derived equivalent - findByActiveTrueOrderByNameAsc() - would work,
     * but it causes the N+1 select problem. Category.department is LAZY, so
     * Hibernate would run one query for the twelve categories and then twelve
     * more, one per row, the moment each department name is read while building
     * the DTOs. Thirteen round trips to a database hosted in another country,
     * to answer a question that is one join.
     *
     * JOIN FETCH tells Hibernate to bring the department back in the SAME
     * query. One round trip, department already populated, LAZY never has to
     * fire. This is the standard fix and the reason the association was left
     * LAZY rather than made EAGER: EAGER would force the extra query
     * everywhere, whereas LAZY plus JOIN FETCH loads the department exactly
     * where it is wanted.
     *
     * "JOIN FETCH", not "LEFT JOIN FETCH": department is optional = false, so a
     * category without one cannot exist and an inner join can never drop a row.
     */
    @Query("SELECT c FROM Category c JOIN FETCH c.department "
            + "WHERE c.active = true ORDER BY c.name ASC")
    List<Category> findSelectableWithDepartment();

    /**
     * The selectable categories belonging to one department, for the cascading
     * "pick a desk, then pick a subject" form F2 will need.
     *
     * Takes the department CODE rather than a Department object so a caller
     * that has only the code from a URL path or query string does not have to
     * load the department first just to pass it in.
     */
    @Query("SELECT c FROM Category c JOIN FETCH c.department d "
            + "WHERE d.code = :departmentCode AND c.active = true ORDER BY c.name ASC")
    List<Category> findSelectableByDepartmentCode(@Param("departmentCode") String departmentCode);

    /**
     * Used by the seeder to decide whether a category already exists, so that
     * starting the application twice does not create duplicates.
     */
    boolean existsByName(String name);
}
