package com.helpdesk.admin;

import com.helpdesk.admin.repository.AdministratorLockRepository;
import com.helpdesk.common.user.entity.Administrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The administrator row lock against the real H2 database (F6-N5).
 *
 * A true race needs two simultaneous transactions, which is slow and flaky in a
 * unit-test run, so this proves the part a mock cannot: that the
 * SELECT ... FOR UPDATE query is valid for the joined Administrator mapping,
 * runs inside a transaction, and returns the active administrators. The
 * behaviour built on top of it (refuse when nobody else is in the list) is
 * covered in UserProvisioningServiceTest.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdministratorLockIntegrationTest {

    @Autowired private AdministratorLockRepository lockRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("the locking query returns the active administrators, including the bootstrap one")
    void locksTheActiveAdministrators() {
        List<Administrator> locked =
                transactionTemplate.execute(status -> lockRepository.lockActiveAdministrators());

        assertThat(locked).isNotEmpty();
        assertThat(locked).allMatch(Administrator::isActive);
        assertThat(locked).extracting(Administrator::getEmail).contains("admin@helpdesk.local");
    }
}
