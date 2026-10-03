package com.helpdesk.ticketportal;

import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.repository.StudentRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F3 #41: finished tickets (RESOLVED or WITHDRAWN) can be archived, and the
 * student can list what they archived at GET /api/tickets/archived.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketArchiveIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger(8000);

    @Autowired private MockMvc mvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Student student;
    private Student otherStudent;

    @BeforeEach
    void setUp() {
        student = saveStudent();
        otherStudent = saveStudent();
    }

    private Student saveStudent() {
        int n = SEQ.incrementAndGet();
        Student s = new Student();
        s.setStudentId("IT" + (26000000 + n));
        s.setFullName("Student " + n);
        s.setEmail("student" + n + "@my.sliit.lk");
        s.setPassword(passwordEncoder.encode("Secret123"));
        s.setDepartment("Faculty of Computing");
        return studentRepository.save(s);
    }

    private Ticket ticket(TicketStatus status) {
        Ticket t = new Ticket();
        t.setStudentId(student.getId());
        t.setSubject("Projector in A501 is broken");
        t.setDescription("No signal from the HDMI port.");
        t.setCategory("Network");
        t.setPriority(TicketPriority.LOW);
        t.setStatus(status);
        return ticketRepository.save(t);
    }

    @Test
    @DisplayName("A RESOLVED ticket archives (204) and appears in the archived list")
    void resolvedTicketArchivesAndIsListed() throws Exception {
        Ticket ticket = ticket(TicketStatus.RESOLVED);

        mvc.perform(post("/api/tickets/" + ticket.getId() + "/archive")
                        .with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/tickets/archived").with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(ticket.getId()))
                .andExpect(jsonPath("$[0].status").value("RESOLVED"));
    }

    @Test
    @DisplayName("A WITHDRAWN ticket is finished too, so it archives (204)")
    void withdrawnTicketArchives() throws Exception {
        Ticket ticket = ticket(TicketStatus.WITHDRAWN);

        mvc.perform(post("/api/tickets/" + ticket.getId() + "/archive")
                        .with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("An OPEN ticket can't be archived (400)")
    void openTicketIsRejected() throws Exception {
        Ticket ticket = ticket(TicketStatus.OPEN);

        mvc.perform(post("/api/tickets/" + ticket.getId() + "/archive")
                        .with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Another student's archived list is empty, and they can't archive your ticket")
    void anotherStudentSeesNothing() throws Exception {
        Ticket ticket = ticket(TicketStatus.RESOLVED);
        mvc.perform(post("/api/tickets/" + ticket.getId() + "/archive")
                        .with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/tickets/archived").with(user(otherStudent.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(post("/api/tickets/" + ticket.getId() + "/archive")
                        .with(user(otherStudent.getEmail()).roles("STUDENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Unarchiving takes the ticket off the archived list")
    void unarchiveRemovesFromList() throws Exception {
        Ticket ticket = ticket(TicketStatus.RESOLVED);
        mvc.perform(post("/api/tickets/" + ticket.getId() + "/archive")
                .with(user(student.getEmail()).roles("STUDENT")));

        mvc.perform(delete("/api/tickets/" + ticket.getId() + "/archive")
                        .with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/tickets/archived").with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("/api/tickets/archived doesn't clash with F2's /api/tickets/{ticketId}")
    void literalPathDoesNotClashWithTicketId() throws Exception {
        Ticket ticket = ticket(TicketStatus.OPEN);

        mvc.perform(get("/api/tickets/" + ticket.getId()).with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticket.getId()));
        mvc.perform(get("/api/tickets/archived").with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
