package com.helpdesk.admin.service;

import com.helpdesk.admin.dto.DashboardResponse;
import com.helpdesk.admin.repository.TicketMetricsRepository;
import com.helpdesk.common.user.repository.AppUserRepository;
import com.helpdesk.ticket.entity.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** DashboardService with the repository mocked: average resolution time and per-department counts. */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private TicketMetricsRepository metrics;
    @Mock private AppUserRepository users;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(metrics, users);
    }

    @Test
    @DisplayName("two tickets in categories of one department are reported under its code")
    void departmentCountsAreKeyedByDepartmentCode() {
        when(metrics.countByDepartment()).thenReturn(List.<Object[]>of(new Object[]{"IT", 2L}));

        DashboardResponse response = service.buildDashboard();

        assertThat(response.ticketsByDepartment()).isEqualTo(Map.of("IT", 2L));
    }

    @Test
    @DisplayName("with nothing resolved the average is null, not zero")
    void averageIsNullWhenNothingIsResolved() {
        when(metrics.resolvedTimestamps(TicketStatus.RESOLVED)).thenReturn(List.of());

        assertThat(service.buildDashboard().averageResolutionHours()).isNull();
    }

    @Test
    @DisplayName("a ticket resolved 3 hours after creation averages 3.0")
    void averageOfOneTicketIsItsDuration() {
        LocalDateTime created = LocalDateTime.of(2026, 10, 1, 9, 0);
        when(metrics.resolvedTimestamps(TicketStatus.RESOLVED))
                .thenReturn(List.<Object[]>of(new Object[]{created, created.plusHours(3)}));

        assertThat(service.buildDashboard().averageResolutionHours()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("the average is rounded to one decimal over several tickets")
    void averageIsRoundedToOneDecimal() {
        LocalDateTime created = LocalDateTime.of(2026, 10, 1, 9, 0);
        // 1h and 2h20m: mean 1h40m = 1.666... hours, which must come back as 1.7.
        when(metrics.resolvedTimestamps(TicketStatus.RESOLVED)).thenReturn(List.<Object[]>of(
                new Object[]{created, created.plusHours(1)},
                new Object[]{created, created.plusHours(2).plusMinutes(20)}));

        assertThat(service.buildDashboard().averageResolutionHours()).isEqualTo(1.7);
    }
}
