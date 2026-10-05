package com.helpdesk.common.user.repository;

import com.helpdesk.common.user.entity.Officer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Repository for officers only (findAll() never returns students). */
public interface OfficerRepository extends JpaRepository<Officer, Long> {

    boolean existsByStaffNumber(String staffNumber);

    Optional<Officer> findByStaffNumber(String staffNumber);

    List<Officer> findByActiveTrueOrderByJobTitleAsc();

    // Fetches the officer only if still active, in one query.
    Optional<Officer> findByIdAndActive(Long id, boolean active);

    // Typed version of AppUserRepository.findByEmail: a non-officer email gives an empty
    // Optional instead of a ClassCastException. Used by the KB to find the logged-in author.
    Optional<Officer> findByEmail(String email);

    /**
     * Active, not removed officers serving a department - the ones to alert about new
     * tickets in that queue (US-04).
     */
    @Query("SELECT DISTINCT o FROM Officer o JOIN o.departments d "
            + "WHERE d.code = :code AND o.active = true AND o.deletedAt IS NULL")
    List<Officer> findActiveServingDepartment(@Param("code") String departmentCode);
}
