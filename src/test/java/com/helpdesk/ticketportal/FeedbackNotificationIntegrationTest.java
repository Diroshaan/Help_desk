package com.helpdesk.ticketportal;

import com.helpdesk.common.reference.entity.Department;
import com.helpdesk.common.reference.repository.DepartmentRepository;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.OfficerRepository;
import com.helpdesk.notification.entity.Notification;
import com.helpdesk.notification.repository.NotificationRepository;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.repository.StudentRepository;
import com.helpdesk.queue.service.QueueService;
import com.helpdesk.queue.service.ResolutionService;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Observer: rating an answer notifies the observers and the answering officer is told. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedbackNotificationIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger(13000);
    private static final String RATED_TITLE = "A student rated your answer";

    @Autowired private MockMvc mvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private OfficerRepository officerRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private QueueService queueService;
    @Autowired private ResolutionService resolutionService;
    @Autowired private PasswordEncoder passwordEncoder;

    private Student student;
    private Officer officer;
    private Department department;

    @BeforeEach
    void setUp() {
        int n = SEQ.incrementAndGet();
        Student s = new Student();
        s.setStudentId("IT" + (26000000 + n));
        s.setFullName("Student " + n);
        s.setEmail("student" + n + "@my.sliit.lk");
        s.setPassword(passwordEncoder.encode("Secret123"));
        s.setDepartment("Faculty of Computing");
        student = studentRepository.save(s);

        n = SEQ.incrementAndGet();
        department = departmentRepository.findAll().get(0);
        Officer o = new Officer("officer" + n + "@helpdesk.local", passwordEncoder.encode("Officer123"),
                "OF" + (30000000 + n), "Support Officer", "Test Officer");
        o.setDepartments(Set.of(department));
        officer = officerRepository.save(o);
    }

    // Routed to the officer's department: an officer can't work an unrouted ticket.
    private Ticket openTicket() {
        Ticket t = new Ticket();
        t.setStudentId(student.getId());
        t.setSubject("Wi-Fi keeps dropping");
        t.setDescription("Disconnects every few minutes in the library.");
        t.setCategory("Network");
        t.setPriority(TicketPriority.MEDIUM);
        t.setStatus(TicketStatus.OPEN);
        t.setAssignedDepartmentId(department.getCode());
        return ticketRepository.save(t);
    }

    private Ticket resolvedTicket() {
        Ticket ticket = openTicket();
        queueService.updateStatus(officer.getId(), ticket.getId(), TicketStatus.IN_PROGRESS);
        resolutionService.create(officer.getId(), ticket.getId(), "Reset the access point", null);
        return ticket;
    }

    private String feedbackJson(int rating) {
        return "{\"rating\":" + rating + ",\"comment\":\"Thanks\"}";
    }

    private List<Notification> ratedNotificationsForOfficer() {
        return notificationRepository.findTop50ByRecipientUserIdOrderByCreatedAtDescIdDesc(officer.getId())
                .stream()
                .filter(notification -> RATED_TITLE.equals(notification.getTitle()))
                .toList();
    }

    @Test
    @DisplayName("Student rates the answer 4/5 -> the officer who wrote it is notified")
    void ratingNotifiesTheAnsweringOfficer() throws Exception {
        Ticket ticket = resolvedTicket();

        mvc.perform(post("/api/tickets/" + ticket.getId() + "/feedback")
                        .with(user(student.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackJson(4)))
                .andExpect(status().isCreated());

        List<Notification> rated = ratedNotificationsForOfficer();
        assertThat(rated).hasSize(1);
        assertThat(rated.get(0).getBody()).contains("Wi-Fi keeps dropping").contains("4/5");
    }

    @Test
    @DisplayName("Changing the rating afterwards doesn't notify the officer a second time")
    void updatingFeedbackDoesNotNotifyAgain() throws Exception {
        Ticket ticket = resolvedTicket();
        mvc.perform(post("/api/tickets/" + ticket.getId() + "/feedback")
                        .with(user(student.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackJson(4)))
                .andExpect(status().isCreated());

        mvc.perform(put("/api/tickets/" + ticket.getId() + "/feedback")
                        .with(user(student.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackJson(2)))
                .andExpect(status().isOk());

        assertThat(ratedNotificationsForOfficer()).hasSize(1);
    }

    @Test
    @DisplayName("Feedback refused (ticket not resolved) notifies no one")
    void refusedFeedbackNotifiesNoOne() throws Exception {
        Ticket ticket = openTicket();
        queueService.updateStatus(officer.getId(), ticket.getId(), TicketStatus.IN_PROGRESS);

        mvc.perform(post("/api/tickets/" + ticket.getId() + "/feedback")
                        .with(user(student.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackJson(5)))
                .andExpect(status().isBadRequest());

        assertThat(ratedNotificationsForOfficer()).isEmpty();
    }
}
