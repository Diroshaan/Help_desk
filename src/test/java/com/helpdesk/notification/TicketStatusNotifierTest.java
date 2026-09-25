package com.helpdesk.notification;

import com.helpdesk.notification.event.TicketStatusChangedEvent;
import com.helpdesk.notification.listener.TicketStatusNotifier;
import com.helpdesk.notification.service.NotificationMessage;
import com.helpdesk.notification.service.NotificationService;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.repository.StudentRepository;
import com.helpdesk.ticket.entity.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for the Observer: what the student is told, and when they aren't told anything. */
@ExtendWith(MockitoExtension.class)
class TicketStatusNotifierTest {

    @Mock private StudentRepository studentRepository;
    @Mock private NotificationService notificationService;

    private TicketStatusChangedEvent event(TicketStatus from, TicketStatus to) {
        return new TicketStatusChangedEvent(12L, 3L, "Wi-Fi not working", from, to);
    }

    private Student activeStudent() {
        Student s = new Student();
        s.setId(3L);
        s.setEmail("nimal@my.sliit.lk");
        s.setFullName("Nimal Perera");
        return s;
    }

    @Test
    @DisplayName("Picked up by an officer: the student is told it's being worked on")
    void inProgressMessage() {
        TicketStatusNotifier notifier = new TicketStatusNotifier(studentRepository, notificationService);
        when(studentRepository.findById(3L)).thenReturn(Optional.of(activeStudent()));

        notifier.onTicketStatusChanged(event(TicketStatus.OPEN, TicketStatus.IN_PROGRESS));

        ArgumentCaptor<NotificationMessage> sent = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationService).notify(any(), sent.capture());
        assertThat(sent.getValue().title()).isEqualTo("Your ticket is being worked on");
        assertThat(sent.getValue().body()).contains("\"Wi-Fi not working\"");
        assertThat(sent.getValue().link()).isEqualTo("#/tickets/12");
    }

    @Test
    @DisplayName("Resolved and reopened each get their own wording")
    void resolvedAndReopened() {
        TicketStatusNotifier notifier = new TicketStatusNotifier(studentRepository, notificationService);
        when(studentRepository.findById(3L)).thenReturn(Optional.of(activeStudent()));
        ArgumentCaptor<NotificationMessage> sent = ArgumentCaptor.forClass(NotificationMessage.class);

        notifier.onTicketStatusChanged(event(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED));
        notifier.onTicketStatusChanged(event(TicketStatus.RESOLVED, TicketStatus.IN_PROGRESS));

        verify(notificationService, org.mockito.Mockito.times(2)).notify(any(), sent.capture());
        assertThat(sent.getAllValues().get(0).title()).isEqualTo("Your ticket has been resolved");
        assertThat(sent.getAllValues().get(1).title()).isEqualTo("Your ticket was reopened");
    }

    @Test
    @DisplayName("A deactivated student is not notified")
    void deactivatedStudentIsSkipped() {
        TicketStatusNotifier notifier = new TicketStatusNotifier(studentRepository, notificationService);
        Student gone = activeStudent();
        gone.setActive(false);
        when(studentRepository.findById(3L)).thenReturn(Optional.of(gone));

        notifier.onTicketStatusChanged(event(TicketStatus.OPEN, TicketStatus.IN_PROGRESS));

        verify(notificationService, never()).notify(any(), any());
    }
}
