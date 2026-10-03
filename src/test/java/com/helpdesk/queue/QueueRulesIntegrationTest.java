package com.helpdesk.queue;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.reference.entity.Department;
import com.helpdesk.common.reference.repository.CategoryRepository;
import com.helpdesk.common.reference.repository.DepartmentRepository;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.OfficerRepository;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.repository.StudentRepository;
import com.helpdesk.queue.service.ResolutionService;
import com.helpdesk.queue.service.QueueService;
import com.helpdesk.queue.service.SupervisionService;
import com.helpdesk.ticket.dto.TicketCreateRequest;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketPriority;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticket.service.TicketService;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F4 final backend: queue rules, resolution authorship, supervisor, routing.
 * Whole application on H2, real services.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QueueRulesIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger(7000);

    @Autowired private MockMvc mvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private OfficerRepository officerRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private QueueService queueService;
    @Autowired private ResolutionService resolutionService;
    @Autowired private SupervisionService supervisionService;
    @Autowired private TicketService ticketService;
    @Autowired private PasswordEncoder passwordEncoder;

    private Student student;
    private Department deptA;
    private Department deptB;
    private Officer officerA;
    private Officer officerA2;
    private Officer officerB;

    @BeforeEach
    void setUp() {
        int n = SEQ.incrementAndGet();
        Student s = new Student();
        s.setStudentId("IT" + (40000000 + n));
        s.setFullName("Test Student");
        s.setEmail("f4student" + n + "@my.sliit.lk");
        s.setPassword(passwordEncoder.encode("Secret123"));
        s.setDepartment("Faculty of Computing");
        student = studentRepository.save(s);

        var departments = departmentRepository.findAll();
        deptA = departments.get(0);
        deptB = departments.get(1);
        officerA = officer(n * 10 + 1, deptA);
        officerA2 = officer(n * 10 + 2, deptA);
        officerB = officer(n * 10 + 3, deptB);
    }

    private Officer officer(int n, Department department) {
        Officer o = new Officer("f4officer" + n + "@helpdesk.local", passwordEncoder.encode("Officer123"),
                "OF" + (50000000 + n), "Support Officer", "Officer " + n);
        o.setDepartments(Set.of(department));
        return officerRepository.save(o);
    }

    private Ticket ticket(String departmentCode, TicketStatus status) {
        Ticket t = new Ticket();
        t.setStudentId(student.getId());
        t.setSubject("Wi-Fi not working");
        t.setDescription("Can't connect in the library.");
        t.setCategory("Network");
        t.setPriority(TicketPriority.MEDIUM);
        t.setStatus(status);
        t.setAssignedDepartmentId(departmentCode);
        return ticketRepository.save(t);
    }

    // ---- Commit 1 ------------------------------------------------------

    @Test
    @DisplayName("The demo seeder runs only with no profile (H2), never on mysql")
    void seederIsDefaultProfileOnly() {
        assertThat(DevQueueDataSeeder.class.getAnnotation(Profile.class).value()).containsExactly("default");
    }

    // ---- Commit 2 ------------------------------------------------------

    @Test
    @DisplayName("An unrouted ticket cannot be worked: updateStatus is 400")
    void unroutedTicketIsReadOnly() {
        Ticket t = ticket(null, TicketStatus.OPEN);
        assertThatThrownBy(() -> queueService.updateStatus(officerA.getId(), t.getId(), TicketStatus.IN_PROGRESS))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Route this ticket");
        // ...but it can still be read for triage
        assertThat(queueService.getQueuedTicket(officerA.getId(), t.getId())).isNotNull();
    }

    @Test
    @DisplayName("Another department's ticket is 404")
    void otherDepartmentIs404() {
        Ticket t = ticket(deptA.getCode(), TicketStatus.OPEN);
        assertThatThrownBy(() -> queueService.updateStatus(officerB.getId(), t.getId(), TicketStatus.IN_PROGRESS))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Assigning a RESOLVED ticket is 400")
    void cannotReRouteResolved() {
        Ticket t = ticket(deptA.getCode(), TicketStatus.RESOLVED);
        assertThatThrownBy(() -> queueService.assignTicket(officerA.getId(), t.getId(), deptB.getCode(), null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Moving an IN_PROGRESS ticket to another department needs a target officer")
    void inProgressMoveNeedsOfficer() {
        Ticket t = ticket(deptA.getCode(), TicketStatus.IN_PROGRESS);
        assertThatThrownBy(() -> queueService.assignTicket(officerA.getId(), t.getId(), deptB.getCode(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("target officer");
    }

    @Test
    @DisplayName("Picking up an unassigned ticket records the officer; someone else's is 400")
    void pickUpClaims() {
        Ticket t = ticket(deptA.getCode(), TicketStatus.OPEN);
        Ticket picked = queueService.updateStatus(officerA.getId(), t.getId(), TicketStatus.IN_PROGRESS);
        assertThat(picked.getAssignedOfficerId()).isEqualTo(officerA.getId());
        assertThat(picked.getAssignedAt()).isNotNull();

        Ticket owned = ticket(deptA.getCode(), TicketStatus.OPEN);
        owned.setAssignedOfficerId(officerA2.getId());
        ticketRepository.save(owned);
        assertThatThrownBy(() -> queueService.updateStatus(officerA.getId(), owned.getId(), TicketStatus.IN_PROGRESS))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("another officer");
    }

    // ---- Commit 3 ------------------------------------------------------

    @Test
    @DisplayName("Only the author edits an answer; the author may")
    void onlyAuthorEdits() {
        Ticket t = inProgressClaimedBy(officerA);
        resolutionService.create(officerA.getId(), t.getId(), "Reset your router.", null);

        assertThatThrownBy(() -> resolutionService.edit(officerA2.getId(), t.getId(), "Hijacked", null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Only the officer who wrote");
        assertThat(resolutionService.edit(officerA.getId(), t.getId(), "Reset the router, then retry.", null)
                .getResponseText()).startsWith("Reset the router");
    }

    // ---- Commit 4 ------------------------------------------------------

    @Test
    @DisplayName("Resolution file: a fake PNG is 400, a real PDF is stored as application/pdf")
    void resolutionFileTypeIsDetectedFromBytes() {
        Ticket t1 = inProgressClaimedBy(officerA);
        var fake = new MockMultipartFile("attachment", "x.png", "image/png",
                "not really a png".getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> resolutionService.create(officerA.getId(), t1.getId(), "Answer", fake))
                .isInstanceOf(ValidationException.class);

        Ticket t2 = inProgressClaimedBy(officerA);
        var pdf = new MockMultipartFile("attachment", "steps.pdf", "text/plain",
                "%PDF-1.4 steps".getBytes(StandardCharsets.US_ASCII));
        var saved = resolutionService.create(officerA.getId(), t2.getId(), "Answer", pdf);
        assertThat(saved.getAttachmentFileType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("Officer downloads the resolution file (200); another department is 404")
    void resolutionFileDownload() throws Exception {
        Ticket t = inProgressClaimedBy(officerA);
        var pdf = new MockMultipartFile("attachment", "steps.pdf", "application/pdf",
                "%PDF-1.4 steps".getBytes(StandardCharsets.US_ASCII));
        resolutionService.create(officerA.getId(), t.getId(), "Answer", pdf);

        mvc.perform(get("/api/queue/" + t.getId() + "/resolution/attachment")
                        .with(user(officerA.getEmail()).roles("OFFICER")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/queue/" + t.getId() + "/resolution/attachment")
                        .with(user(officerB.getEmail()).roles("OFFICER")))
                .andExpect(status().isNotFound());
    }

    // ---- Commit 5 ------------------------------------------------------

    @Test
    @DisplayName("Supervisor: A->B allowed, B->A is a loop, self is rejected, supervisor can edit")
    void supervisorRules() {
        supervisionService.assignSupervisor(officerA2.getId(), officerA.getId());
        assertThatThrownBy(() -> supervisionService.assignSupervisor(officerA.getId(), officerA2.getId()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("loop");
        assertThatThrownBy(() -> supervisionService.assignSupervisor(officerA.getId(), officerA.getId()))
                .isInstanceOf(ValidationException.class);

        // officerA supervises officerA2, so A may edit A2's answer
        Ticket t = inProgressClaimedBy(officerA2);
        resolutionService.create(officerA2.getId(), t.getId(), "A2's answer", null);
        assertThat(resolutionService.edit(officerA.getId(), t.getId(), "Supervisor's fix", null)
                .getResponseText()).isEqualTo("Supervisor's fix");

        assertThat(supervisionService.assignSupervisor(officerA2.getId(), null).getSupervisor()).isNull();
    }

    // ---- Commit 6/8 ----------------------------------------------------

    @Test
    @DisplayName("Officer lists attachments and history in their department; another department is 404; student 403")
    void attachmentsAndHistoryAreScoped() throws Exception {
        Ticket t = ticket(deptA.getCode(), TicketStatus.OPEN);

        mvc.perform(get("/api/queue/" + t.getId() + "/attachments")
                        .with(user(officerA.getEmail()).roles("OFFICER"))).andExpect(status().isOk());
        mvc.perform(get("/api/queue/" + t.getId() + "/history")
                        .with(user(officerA.getEmail()).roles("OFFICER"))).andExpect(status().isOk());
        mvc.perform(get("/api/queue/" + t.getId() + "/attachments")
                        .with(user(officerB.getEmail()).roles("OFFICER"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/queue/" + t.getId() + "/history")
                        .with(user(officerB.getEmail()).roles("OFFICER"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/queue/" + t.getId() + "/attachments")
                        .with(user(student.getEmail()).roles("STUDENT"))).andExpect(status().isForbidden());
    }

    // ---- Commit 7 ------------------------------------------------------

    @Test
    @DisplayName("A new ticket is routed to the department that owns its category")
    void newTicketIsRouted() {
        var category = categoryRepository.findSelectableWithDepartment().get(0);
        TicketCreateRequest request = new TicketCreateRequest();
        request.setSubject("Cannot log in");
        request.setDescription("Password reset link expired");
        request.setCategory(category.getName());
        request.setPriority(TicketPriority.MEDIUM);

        Ticket created = ticketService.createTicket(student.getId(), request);

        assertThat(created.getAssignedDepartmentId()).isEqualTo(category.getDepartment().getCode());
    }

    private Ticket inProgressClaimedBy(Officer officer) {
        Ticket t = ticket(officer.getDepartments().iterator().next().getCode(), TicketStatus.OPEN);
        return queueService.updateStatus(officer.getId(), t.getId(), TicketStatus.IN_PROGRESS);
    }
}
