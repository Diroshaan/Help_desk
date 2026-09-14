package com.helpdesk.common.user.repository;

import com.helpdesk.common.user.entity.Officer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * SHARED USER MODEL - not owned by any single feature.
 *
 * Data access for Help Desk Officers.
 *
 * Separate from AppUserRepository because the questions are different. This one
 * answers "which officers are there?" - findAll() here returns officers only,
 * never students - whereas AppUserRepository answers "who holds this email?"
 * across every type. Having both is the point of the hierarchy: the shared
 * question is asked once at the top, and the type-specific questions stay with
 * the type.
 *
 * F4 will need findByDepartment once Officer gains its departments association;
 * F6 will need the write side to provision accounts. Neither is here, because
 * neither is shared user-model work.
 */
public interface OfficerRepository extends JpaRepository<Officer, Long> {

    /** Used when provisioning, to reject a staff number already issued. */
    boolean existsByStaffNumber(String staffNumber);

    Optional<Officer> findByStaffNumber(String staffNumber);

    /**
     * Every officer who can currently be assigned work.
     *
     * Filtering on the inherited 'active' flag works exactly as it would on a
     * field declared here - Spring Data resolves the property against the whole
     * mapped hierarchy, and Hibernate puts the condition on the joined users
     * table. Worth knowing, because the alternative most people reach for is
     * loading every officer and filtering in Java, which asks the database for
     * rows only to throw them away.
     */
    List<Officer> findByActiveTrueOrderByJobTitleAsc();

    /**
     * F4's "is this officer allowed to act" lookup: fetch by id only if still
     * active, in one query rather than findById() followed by a Java-side
     * isActive() check.
     */
    Optional<Officer> findByIdAndActive(Long id, boolean active);
}
