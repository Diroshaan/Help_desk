package com.helpdesk.common.user.repository;

import com.helpdesk.common.user.entity.Administrator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * SHARED USER MODEL - not owned by any single feature.
 *
 * Data access for System Administrators.
 *
 * Thin, like the entity it serves, and for the same reason: an administrator's
 * distinguishing work is done through the things it points AT - the accounts it
 * provisioned, the announcements it published - and those associations belong to
 * F6. What lives here is only what the shared user model needs.
 */
public interface AdministratorRepository extends JpaRepository<Administrator, Long> {

    boolean existsByStaffNumber(String staffNumber);

    List<Administrator> findByActiveTrueOrderByDisplayNameAsc();

    /**
     * Whether any usable administrator account exists at all.
     *
     * The bootstrap question. A freshly created database has no administrator,
     * and no way to create one through the application - provisioning privileged
     * accounts is itself an administrator action, so the first one has to come
     * from somewhere else. F6 will use this to decide whether to seed a
     * first-run account, and the same check is what stops it seeding a second
     * one on every restart.
     *
     * Counting only ACTIVE administrators, not all of them, is the part worth
     * getting right: a deployment whose only administrator has been deactivated
     * is locked out just as thoroughly as one that never had an administrator,
     * and existsBy() over all rows would report everything is fine.
     */
    boolean existsByActiveTrue();

    /**
     * Same question as existsByActiveTrue(), but excluding one specific
     * administrator - the one currently being deactivated.
     *
     * Without excluding self, deactivating the LAST active administrator
     * would ask "does any active administrator exist?", find the one
     * about to be deactivated still marked active (the flip hasn't
     * happened yet), and wrongly allow the lockout this check exists to
     * prevent.
     */
    boolean existsByActiveTrueAndIdNot(Long id);
}
