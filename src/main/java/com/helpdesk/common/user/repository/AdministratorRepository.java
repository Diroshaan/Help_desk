package com.helpdesk.common.user.repository;

import com.helpdesk.common.user.entity.Administrator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Repository for administrators. */
public interface AdministratorRepository extends JpaRepository<Administrator, Long> {

    boolean existsByStaffNumber(String staffNumber);

    List<Administrator> findByActiveTrueOrderByDisplayNameAsc();

    // Bootstrap check: is there any active admin? A deployment whose only admin is
    // deactivated is just as locked out as one with none.
    boolean existsByActiveTrue();

    // Same check, ignoring the admin being deactivated, so the last active admin can't be switched off.
    boolean existsByActiveTrueAndIdNot(Long id);
}
