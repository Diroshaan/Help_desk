package com.helpdesk.common.user.repository;

import com.helpdesk.common.user.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * SHARED USER MODEL - not owned by any single feature.
 *
 * Data access across EVERY account type.
 *
 * Because AppUser is the root of a JOINED hierarchy, a query through this
 * repository is polymorphic: findByEmail returns whatever kind of account holds
 * that address, already constructed as the right Java type. Hibernate does the
 * work by left-joining the three subtype tables and using whichever one has a
 * matching row to decide which class to instantiate. Calling code can then just
 * ask user.getRole() without knowing or caring which table the row came from.
 *
 * That is the single most useful thing the JOINED strategy gives this project,
 * and it is what lets StudentUserDetailsService authenticate a student, an
 * officer or an administrator through one code path instead of three.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /**
     * Find any account - student, officer or administrator - by its login
     * address.
     *
     * This is the method that makes officer and admin login possible. Before the
     * user supertype existed, authentication went through StudentRepository, so
     * an account that was not a student simply could not log in: the lookup
     * searched one table and that table only ever held students. The access
     * rules in SecurityConfig referring to ROLE_OFFICER and ROLE_ADMIN were
     * therefore unreachable - correct rules guarding endpoints that no
     * authenticated user could ever satisfy.
     *
     * Returns Optional rather than null: the caller has to decide what "no such
     * account" means, and the type makes forgetting to decide a compile error
     * rather than a NullPointerException later.
     */
    Optional<AppUser> findByEmail(String email);

    /**
     * Used before creating any account, of any type.
     *
     * Email is unique across the whole hierarchy because the constraint lives on
     * the users table, so this one question covers all three types. Note this is
     * still only a pre-check that produces a friendly message - the guarantee
     * comes from the database constraint, which two simultaneous registrations
     * cannot race past.
     */
    boolean existsByEmail(String email);
}
