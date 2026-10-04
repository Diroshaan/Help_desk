package com.helpdesk.common.reference.repository;

import com.helpdesk.common.reference.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // Active categories with their department loaded in the same query (JOIN FETCH avoids N+1).
    @Query("SELECT c FROM Category c JOIN FETCH c.department "
            + "WHERE c.active = true ORDER BY c.name ASC")
    List<Category> findSelectableWithDepartment();

    // Active categories for one department, looked up by its code.
    @Query("SELECT c FROM Category c JOIN FETCH c.department d "
            + "WHERE d.code = :departmentCode AND c.active = true ORDER BY c.name ASC")
    List<Category> findSelectableByDepartmentCode(@Param("departmentCode") String departmentCode);

    // Used by the seeder so restarts don't create duplicates.
    boolean existsByName(String name);

    // Ticket stores the category name, so this finds which department a ticket belongs to
    // (used for the new-ticket alert to officers).
    @Query("SELECT c FROM Category c JOIN FETCH c.department WHERE c.name = :name")
    Optional<Category> findByNameWithDepartment(@Param("name") String name);
}
