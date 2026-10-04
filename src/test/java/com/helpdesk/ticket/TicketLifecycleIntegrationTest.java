package com.helpdesk.ticket;

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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ticket edits on the full app: length limits, optimistic locking (409) and the page number cap. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketLifecycleIntegrationTest {

    /** Unique ids per test - the H2 database is shared by every test in the run. */
    private static final AtomicInteger SEQ = new AtomicInteger(3000);

    @Autowired private MockMvc mvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Student student;

    @BeforeEach
    void setUp() {
        int n = SEQ.incrementAndGet();
        Student s = new Student();
        s.setStudentId("IT" + (29000000 + n));
        s.setFullName("Saman Kumara");
        s.setEmail("f2lifecycle" + n + "@my.sliit.lk");
        s.setPassword(passwordEncoder.encode("Secret123"));
        s.setDepartment("Faculty of Computing");
        student = studentRepository.save(s);
    }

    private Ticket openTicket() {
        Ticket t = new Ticket();
        t.setStudentId(student.getId());
        t.setSubject("Can't connect to Wi-Fi");
        t.setDescription("The library Wi-Fi keeps dropping.");
        t.setCategory("Network");
        t.setPriority(TicketPriority.MEDIUM);
        t.setStatus(TicketStatus.OPEN);
        return ticketRepository.save(t);
    }

    private String createRequestJson(String subject, String description, String category) {
        return "{\"subject\":\"" + subject + "\",\"description\":\"" + description
                + "\",\"category\":\"" + category + "\",\"priority\":\"MEDIUM\"}";
    }

    @Test
    @DisplayName("A description over 2000 characters is rejected with a message naming the field")
    void overLongDescriptionIsRejected() throws Exception {
        String tooLong = "x".repeat(2001);

        mvc.perform(post("/api/tickets")
                        .with(user(student.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson("Wi-Fi issue", tooLong, "Password & account access")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Description")));
    }

    @Test
    @DisplayName("A description of exactly 2000 characters is accepted")
    void maxLengthDescriptionIsAccepted() throws Exception {
        String exactlyMax = "x".repeat(2000);

        mvc.perform(post("/api/tickets")
                        .with(user(student.getEmail()).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson("Wi-Fi issue", exactlyMax, "Password & account access")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Withdrawing a ticket that is not OPEN is refused")
    void withdrawingNonOpenTicketIsRefused() throws Exception {
        Ticket ticket = openTicket();
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticketRepository.save(ticket);

        mvc.perform(post("/api/tickets/" + ticket.getId() + "/withdraw")
                        .with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isBadRequest());
    }

    // @Version makes the second save fail with 409 instead of silently overwriting the first.
    @Test
    @DisplayName("Two concurrent edits of the same ticket: the second save gets 409")
    void concurrentEditsConflict() {
        Ticket ticket = openTicket();

        // Two separate copies of the row, like two browser tabs.
        Ticket firstCopy = ticketRepository.findById(ticket.getId()).orElseThrow();
        Ticket secondCopy = ticketRepository.findById(ticket.getId()).orElseThrow();

        firstCopy.setSubject("Updated by the first save");
        ticketRepository.save(firstCopy);
        ticketRepository.flush();

        secondCopy.setSubject("Updated by the second, stale save");
        assertThatThrownBy(() -> {
            ticketRepository.save(secondCopy);
            ticketRepository.flush();
        }).isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
    }

    // size=100 too: at the default size of 20 this page number does not overflow int,
    // so the test would pass even without the cap.
    @Test
    @DisplayName("An enormous page number returns an empty page instead of a 500")
    void hugePageNumberIsClamped() throws Exception {
        openTicket();

        mvc.perform(get("/api/tickets/search")
                        .param("page", "21474837")
                        .param("size", "100")
                        .with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }
}
