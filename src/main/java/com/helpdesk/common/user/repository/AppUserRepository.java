package com.helpdesk.common.user.repository;

import com.helpdesk.common.user.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository across all account types. Queries are polymorphic (JOINED), so findByEmail
 * returns a Student, Officer or Administrator and login works through one code path.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    // Friendly pre-check before creating an account; the unique constraint is the real guarantee.
    boolean existsByEmail(String email);
}
