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

/** The SELECT ... FOR UPDATE lock on active administrators, run on real H2 (the race itself is not tested). */
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
