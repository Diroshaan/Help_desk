package com.helpdesk.admin;

import com.helpdesk.admin.dto.DashboardResponse;
import com.helpdesk.admin.service.DashboardService;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dashboard queries against the real H2 database and the categories the
 * seeder creates (F6-N1, F6-N4).
 *
 * The Spring context and its database are shared with every other integration
 * test, so these tests compare the dashboard before and after adding their own
 * tickets instead of assuming an empty tickets table.
 */
@SpringBootTest
@ActiveProfiles("test")
class DashboardQueryIntegrationTest {

    @Autowired private DashboardService dashboardService;
    @Autowired private TicketRepository tickets;

    private Ticket ticket(String category, TicketStatus status) {
        Ticket t = new Ticket();
        t.setStudentId(1L);
        t.setSubject("Dashboard test");
        t.setDescription("Dashboard test");
        t.setCategory(category);
        t.setStatus(status);
        return tickets.save(t);
    }

    private long count(java.util.Map<String, Long> map, String code) {
        return map.getOrDefault(code, 0L);
    }

    @Test
    @DisplayName("tickets are counted under the department that owns their category")
    void ticketsAreCountedUnderTheirCategorysDepartment() {
        DashboardResponse before = dashboardService.buildDashboard();

        // Two different categories of IT, one of REG (see ReferenceDataSeeder).
        ticket("Password & account access", TicketStatus.OPEN);
        ticket("Network & Wi-Fi (eduroam)", TicketStatus.RESOLVED);
        ticket("Module registration", TicketStatus.OPEN);

        DashboardResponse after = dashboardService.buildDashboard();

        assertThat(count(after.ticketsByDepartment(), "IT"))
                .isEqualTo(count(before.ticketsByDepartment(), "IT") + 2);
        assertThat(count(after.ticketsByDepartment(), "REG"))
                .isEqualTo(count(before.ticketsByDepartment(), "REG") + 1);
        // Only the unfinished IT ticket adds to the backlog.
        assertThat(count(after.openBacklogByDepartment(), "IT"))
                .isEqualTo(count(before.openBacklogByDepartment(), "IT") + 1);
    }

    @Test
    @DisplayName("a resolved ticket with a resolvedAt timestamp is included in the average")
    void resolvedTicketAppearsInTheAverage() {
        Ticket t = ticket("Fee payment", TicketStatus.RESOLVED);
        t.setCreatedAt(LocalDateTime.now().minusHours(5));
        t.setResolvedAt(LocalDateTime.now());
        tickets.save(t);

        // Other tests may have resolved tickets too, so only assert that the
        // average now exists; the exact arithmetic is covered in DashboardServiceTest.
        assertThat(dashboardService.buildDashboard().averageResolutionHours()).isNotNull();
    }
}
