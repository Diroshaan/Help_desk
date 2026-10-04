package com.helpdesk.admin.repository;

import com.helpdesk.common.user.entity.Administrator;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * Locks the active administrator rows (SELECT ... FOR UPDATE) so two deactivations at
 * once take turns and we always keep at least one active admin. Must be called inside a
 * transaction. Extends the bare Repository so it only exposes this one query.
 */
public interface AdministratorLockRepository extends Repository<Administrator, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Administrator a WHERE a.active = true")
    List<Administrator> lockActiveAdministrators();
}
