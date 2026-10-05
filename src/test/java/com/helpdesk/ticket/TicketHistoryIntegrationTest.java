package com.helpdesk.ticket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.common.reference.repository.DepartmentRepository;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.OfficerRepository;
import com.helpdesk.notification.event.TicketStatusChangedEvent;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.repository.StudentRepository;
import com.helpdesk.queue.service.QueueService;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketPriority;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticket.repository.TicketStatusChangeRepository;
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

/** Status-change history through the real QueueService and event, inside the real transaction. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketHistoryIntegrationTest {

    /** Unique ids per test - the H2 database is shared by every test in the run. */
    private static final AtomicInteger SEQ = new AtomicInteger(4000);

    @Autowired private MockMvc mvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private OfficerRepository officerRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private TicketStatusChangeRepository historyRepository;
    @Autowired private QueueService queueService;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private Student student;
    private Officer officer;
    private com.helpdesk.common.reference.entity.Department department;

    @BeforeEach
    void setUp() {
        int n = SEQ.incrementAndGet();
        Student s = new Student();
        s.setStudentId("IT" + (31000000 + n));
        s.setFullName("Kavindu Jay");
        s.setEmail("f2history" + n + "@my.sliit.lk");
        s.setPassword(passwordEncoder.encode("Secret123"));
        s.setDepartment("Faculty of Computing");
        student = studentRepository.save(s);

        department = departmentRepository.findAll().get(0);
        Officer o = new Officer("officer" + n + "@helpdesk.local", passwordEncoder.encode("Officer123"),
                "OF" + (32000000 + n), "Support Officer", "Test Officer");
        o.setDepartments(Set.of(department));
        officer = officerRepository.save(o);
    }

    // Goes through the real HTTP create, so the history row is written in the same transaction.
    @Test
    @DisplayName("Submitting a ticket records one history entry: null -> OPEN, by the student")
    void creatingATicketRecordsTheFirstEntry() throws Exception {
        String json = "{\"subject\":\"Can't log in\",\"description\":\"Password reset link expired\","
                + "\"category\":\"Password & account access\",\"priority\":\"MEDIUM\"}";

        String body = mvc.perform(post("/api/tickets")
                        .with(user(student.getEmail()).roles("STUDENT"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long ticketId = objectMapper.readTree(body).get("id").asLong();

        mvc.perform(get("/api/tickets/" + ticketId + "/history")
                        .with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sequenceNo").value(1))
                .andExpect(jsonPath("$[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$[0].toStatus").value("OPEN"))
                .andExpect(jsonPath("$[0].changedBy").value("Student"));
    }

    @Test
    @DisplayName("An officer moving the ticket to IN_PROGRESS appends a second entry")
    void officerStatusChangeAppendsASecondEntry() throws Exception {
        Ticket ticket = openTicket();
        // Saved straight to the repository, which skips create(), so add entry 1 by hand.
        historyRepository.save(new com.helpdesk.ticket.entity.TicketStatusChange(
                ticket.getId(), 1, null, TicketStatus.OPEN, student.getId(), java.time.LocalDateTime.now()));

        // The officer id is passed, so the history names the officer.
        queueService.updateStatus(officer.getId(), ticket.getId(), TicketStatus.IN_PROGRESS);

        mvc.perform(get("/api/tickets/" + ticket.getId() + "/history")
                        .with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].sequenceNo").value(2))
                .andExpect(jsonPath("$[1].fromStatus").value("OPEN"))
                .andExpect(jsonPath("$[1].toStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[1].changedBy").value("Test Officer"));
    }

    @Test
    @DisplayName("Another student reading the history gets 404")
    void anotherStudentGets404() throws Exception {
        Ticket ticket = openTicket();
        Student other = new Student();
        other.setStudentId("IT" + (33000000 + SEQ.incrementAndGet()));
        other.setFullName("Other Student");
        other.setEmail("f2historyother" + SEQ.incrementAndGet() + "@my.sliit.lk");
        other.setPassword(passwordEncoder.encode("Secret123"));
        other.setDepartment("Faculty of Computing");
        other = studentRepository.save(other);

        mvc.perform(get("/api/tickets/" + ticket.getId() + "/history")
                        .with(user(other.getEmail()).roles("STUDENT")))
                .andExpect(status().isNotFound());
    }

    // The recorder is a plain @EventListener so it runs in the same transaction;
    // a rolled-back change must leave no history row.
    @Test
    @DisplayName("If the status change rolls back, no history row exists")
    void rolledBackChangeLeavesNoHistoryRow() {
        Ticket ticket = openTicket();

        transactionTemplate.executeWithoutResult(tx -> {
            eventPublisher.publishEvent(new TicketStatusChangedEvent(
                    ticket.getId(), student.getId(), ticket.getSubject(),
                    TicketStatus.OPEN, TicketStatus.IN_PROGRESS));
            tx.setRollbackOnly();
        });

        assertThat(historyRepository.findByTicketIdOrderBySequenceNoAsc(ticket.getId())).isEmpty();
    }

    private Ticket openTicket() {
        Ticket t = new Ticket();
        t.setStudentId(student.getId());
        t.setSubject("Wi-Fi not working");
        t.setDescription("Can't connect in the library.");
        t.setCategory("Network");
        t.setPriority(TicketPriority.MEDIUM);
        t.setStatus(TicketStatus.OPEN);
        // Officers can't work an unrouted ticket.
        t.setAssignedDepartmentId(department.getCode());
        return ticketRepository.save(t);
    }
}
