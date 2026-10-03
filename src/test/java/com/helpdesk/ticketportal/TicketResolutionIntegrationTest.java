package com.helpdesk.ticketportal;

import com.helpdesk.common.reference.entity.Department;
import com.helpdesk.common.reference.repository.DepartmentRepository;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.OfficerRepository;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.repository.StudentRepository;
import com.helpdesk.queue.service.QueueService;
import com.helpdesk.queue.service.ResolutionService;
import com.helpdesk.queue.service.StaffNoteService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F3, US WBHD-24: a student reads the officer's answer to their own ticket,
 * through the real F4 services that write it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketResolutionIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger(12000);

    @Autowired private MockMvc mvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private OfficerRepository officerRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private QueueService queueService;
    @Autowired private ResolutionService resolutionService;
    @Autowired private StaffNoteService staffNoteService;
    @Autowired private PasswordEncoder passwordEncoder;

    private Student student;
    private Student otherStudent;
    private Officer officer;
    private Department department;

    @BeforeEach
    void setUp() {
        student = saveStudent();
        otherStudent = saveStudent();

        int n = SEQ.incrementAndGet();
        department = departmentRepository.findAll().get(0);
        Officer o = new Officer("officer" + n + "@helpdesk.local", passwordEncoder.encode("Officer123"),
                "OF" + (30000000 + n), "Support Officer", "Test Officer");
        o.setDepartments(Set.of(department));
        officer = officerRepository.save(o);
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

    // Routed to the officer's department: an officer can't work an unrouted ticket.
    private Ticket openTicket() {
        Ticket t = new Ticket();
        t.setStudentId(student.getId());
        t.setSubject("Can't log in to the LMS");
        t.setDescription("Password reset link never arrives.");
        t.setCategory("Network");
        t.setPriority(TicketPriority.MEDIUM);
        t.setStatus(TicketStatus.OPEN);
        t.setAssignedDepartmentId(department.getCode());
        return ticketRepository.save(t);
    }

    private Ticket resolvedTicket(MockMultipartFile attachment) {
        Ticket ticket = openTicket();
        queueService.updateStatus(officer.getId(), ticket.getId(), TicketStatus.IN_PROGRESS);
        resolutionService.create(officer.getId(), ticket.getId(), "Reset done", attachment);
        return ticket;
    }

    @Test
    @DisplayName("The owner reads the answer, with the officer's name")
    void ownerReadsTheAnswer() throws Exception {
        Ticket ticket = resolvedTicket(null);

        mvc.perform(get("/api/tickets/" + ticket.getId() + "/resolution")
                        .with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseText").value("Reset done"))
                .andExpect(jsonPath("$.officerName").value("Test Officer"))
                .andExpect(jsonPath("$.publishedAt").exists());
    }

    @Test
    @DisplayName("Another student gets 404, not 403 - the ticket id is never confirmed")
    void anotherStudentGetsNotFound() throws Exception {
        Ticket ticket = resolvedTicket(null);

        mvc.perform(get("/api/tickets/" + ticket.getId() + "/resolution")
                        .with(user(otherStudent.getEmail()).roles("STUDENT")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/tickets/" + ticket.getId() + "/resolution/attachment")
                        .with(user(otherStudent.getEmail()).roles("STUDENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A ticket with no answer yet is 404")
    void noAnswerYetIsNotFound() throws Exception {
        Ticket ticket = openTicket();

        mvc.perform(get("/api/tickets/" + ticket.getId() + "/resolution")
                        .with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Staff notes never appear in the student's view of the answer")
    void staffNotesStayHidden() throws Exception {
        Ticket ticket = openTicket();
        queueService.updateStatus(officer.getId(), ticket.getId(), TicketStatus.IN_PROGRESS);
        staffNoteService.create(officer.getId(), ticket.getId(), "INTERNAL-ONLY: student was rude on the phone");
        resolutionService.create(officer.getId(), ticket.getId(), "Reset done", null);

        mvc.perform(get("/api/tickets/" + ticket.getId() + "/resolution")
                        .with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("INTERNAL-ONLY"))));
    }

    @Test
    @DisplayName("The owner downloads the answer's file as an attachment, with nosniff")
    void ownerDownloadsTheFile() throws Exception {
        byte[] pdf = "%PDF-1.4 steps".getBytes(StandardCharsets.US_ASCII);
        Ticket ticket = resolvedTicket(new MockMultipartFile("file", "steps.pdf", "application/pdf", pdf));

        mvc.perform(get("/api/tickets/" + ticket.getId() + "/resolution/attachment")
                        .with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().bytes(pdf));
    }

    @Test
    @DisplayName("An answer without a file: the download is 404")
    void noFileIsNotFound() throws Exception {
        Ticket ticket = resolvedTicket(null);

        mvc.perform(get("/api/tickets/" + ticket.getId() + "/resolution/attachment")
                        .with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isNotFound());
    }
}
