package com.helpdesk.admin.repository;

import com.helpdesk.common.user.entity.Administrator;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * F6 - the row lock behind the "always one active administrator" rule.
 *
 * Reading the active administrators with PESSIMISTIC_WRITE (SELECT ... FOR
 * UPDATE) makes two simultaneous deactivations take turns: the second waits for
 * the first to commit and then sees the real state. See
 * UserProvisioningService.requireAnotherActiveAdmin for the full reasoning.
 *
 * It lives here and not on AdministratorRepository because that file belongs to
 * the shared common package, and it extends the bare Repository marker so it
 * exposes this one query and nothing else (no save, no delete).
 *
 * The lock is only held for the length of the caller's transaction, so it must
 * be called from inside a @Transactional method; outside one, Spring Data
 * refuses to run a locking query.
 */
public interface AdministratorLockRepository extends Repository<Administrator, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Administrator a WHERE a.active = true")
    List<Administrator> lockActiveAdministrators();
}
