package com.helpdesk.common.reference.repository;

import com.helpdesk.common.reference.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * SHARED REFERENCE DATA - not owned by any single feature.
 *
 * Data access for support departments.
 *
 * The primary key type is String, not Long, because Department uses its
 * natural code as the key - see the comment on Department.code for why. That
 * is the only thing unusual about this interface; JpaRepository does not care
 * what the key type is as long as the second type parameter matches the field
 * annotated @Id.
 */
public interface DepartmentRepository extends JpaRepository<Department, String> {

    /**
     * The departments a student may actually choose from.
     *
     * A derived query rather than findAll() with filtering in Java: the WHERE
     * clause runs in the database, so a retired department is never transferred
     * over the network or held in memory just to be discarded. With six rows
     * that difference is invisible, but the habit is the point - the same
     * mistake made against the tickets table is a real performance problem, and
     * it is easier to always write the narrower query than to remember which
     * tables are small.
     *
     * Ordered by name so the dropdown is stable and alphabetical. Without an
     * explicit ORDER BY, the database is free to return rows in any order it
     * likes, and that order can change between runs - which looks like a bug to
     * a user who watched the list reshuffle itself.
     */
    List<Department> findByActiveTrueOrderByNameAsc();
}
