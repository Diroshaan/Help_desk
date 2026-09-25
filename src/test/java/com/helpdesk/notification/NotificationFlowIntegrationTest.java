package com.helpdesk.notification;

import com.helpdesk.common.reference.entity.Department;
import com.helpdesk.common.reference.repository.DepartmentRepository;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.OfficerRepository;
import com.helpdesk.notification.event.TicketStatusChangedEvent;
import com.helpdesk.notification.repository.NotificationRepository;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.repository.StudentRepository;
import com.helpdesk.queue.service.QueueService;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketPriority;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end: an officer changes a ticket's status through the real
 * QueueService, the event is published, the listener runs after commit,
 * and the student finds the message in their portal inbox over HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationFlowIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger(5000);

    @Autowired private MockMvc mvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private OfficerRepository officerRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private QueueService queueService;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private PasswordEncoder passwordEncoder;

    private Student student;
    private Officer officer;

    @BeforeEach
    void setUp() {
        int n = SEQ.incrementAndGet();
        Student s = new Student();
        s.setStudentId("IT" + (26000000 + n));
        s.setFullName("Nimal Perera");
        s.setEmail("nimal" + n + "@my.sliit.lk");
        s.setPassword(passwordEncoder.encode("Secret123"));
        s.setDepartment("Faculty of Computing");
        student = studentRepository.save(s);

        Department department = departmentRepository.findAll().get(0);
        Officer o = new Officer("officer" + n + "@helpdesk.local", passwordEncoder.encode("Officer123"),
                "OF" + (30000000 + n), "Support Officer", "Test Officer");
        o.setDepartments(Set.of(department));
        officer = officerRepository.save(o);
    }

    private Ticket openTicket() {
        Ticket t = new Ticket();
        t.setStudentId(student.getId());
        t.setSubject("Wi-Fi not working");
        t.setDescription("Can't connect in the library.");
        t.setCategory("Network");
        t.setPriority(TicketPriority.MEDIUM);
        t.setStatus(TicketStatus.OPEN);
        return ticketRepository.save(t);
    }

    @Test
    @DisplayName("Officer picks up a ticket -> the student sees a notification in their inbox")
    void statusChangeReachesTheStudentsInbox() throws Exception {
        Ticket ticket = openTicket();

        queueService.updateStatus(officer.getId(), ticket.getId(), TicketStatus.IN_PROGRESS);

        mvc.perform(get("/api/notifications").with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Your ticket is being worked on"))
                .andExpect(jsonPath("$[0].link").value("#/tickets/" + ticket.getId()))
                .andExpect(jsonPath("$[0].read").value(false));
        mvc.perform(get("/api/notifications/unread-count").with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(jsonPath("$.unread").value(1));
    }

    @Test
    @DisplayName("Portal alerts switched off -> nothing is written to the inbox")
    void portalOffMeansNoInboxEntry() {
        student.setPortalNotificationsEnabled(false);
        studentRepository.save(student);
        Ticket ticket = openTicket();

        queueService.updateStatus(officer.getId(), ticket.getId(), TicketStatus.IN_PROGRESS);

        assertThat(notificationRepository.countByRecipientUserIdAndReadAtIsNull(student.getId())).isZero();
    }

    @Test
    @DisplayName("If the status change rolls back, the student is not told about it")
    void rolledBackChangeSendsNothing() {
        transactionTemplate.executeWithoutResult(tx -> {
            eventPublisher.publishEvent(new TicketStatusChangedEvent(
                    999L, student.getId(), "Never happened", TicketStatus.OPEN, TicketStatus.IN_PROGRESS));
            tx.setRollbackOnly();
        });

        assertThat(notificationRepository.countByRecipientUserIdAndReadAtIsNull(student.getId())).isZero();
    }

    @Test
    @DisplayName("Marking read: your own works, someone else's is 'not found'")
    void markReadIsOwnerOnly() throws Exception {
        Ticket ticket = openTicket();
        queueService.updateStatus(officer.getId(), ticket.getId(), TicketStatus.IN_PROGRESS);
        Long id = notificationRepository
                .findTop50ByRecipientUserIdOrderByCreatedAtDescIdDesc(student.getId()).get(0).getId();

        mvc.perform(post("/api/notifications/" + id + "/read").with(user(officer.getEmail()).roles("OFFICER")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/notifications/" + id + "/read").with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    @DisplayName("Signed-out callers can't read an inbox")
    void anonymousIsRefused() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isForbidden());
    }
}
