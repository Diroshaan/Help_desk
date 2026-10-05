package com.helpdesk.notification;

import com.helpdesk.notification.event.TicketStatusChangedEvent;
import com.helpdesk.ticket.entity.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** TicketStatusChangedEvent carries the officer; the older five-argument form means "unknown officer". */
class TicketStatusChangedEventTest {

    @Test
    @DisplayName("The six-value form carries the officer who made the change")
    void carriesTheOfficer() {
        TicketStatusChangedEvent event = new TicketStatusChangedEvent(
                12L, 3L, "Wi-Fi", TicketStatus.OPEN, TicketStatus.IN_PROGRESS, 40L);

        assertThat(event.changedByOfficerId()).isEqualTo(40L);
    }

    @Test
    @DisplayName("The original five-value form still compiles and records the officer as unknown")
    void originalFormMeansUnknownOfficer() {
        TicketStatusChangedEvent event = new TicketStatusChangedEvent(
                12L, 3L, "Wi-Fi", TicketStatus.OPEN, TicketStatus.IN_PROGRESS);

        assertThat(event.changedByOfficerId()).isNull();
        assertThat(event.toStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }
}
