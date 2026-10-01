package com.helpdesk.ticket;

import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.repository.StudentRepository;
import com.helpdesk.ticket.entity.Attachment;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketPriority;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.repository.AttachmentRepository;
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

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F2 attachments over HTTP, against the whole running application.
 *
 * The full application rather than a mock because the metadata listing is a
 * JPQL constructor expression: whether it matches AttachmentResponse's
 * constructor, and whether the JSON a browser receives has the fields the
 * frontend reads, can only be proved with a real query on a real database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketAttachmentIntegrationTest {

    /** Unique ids per test - the H2 database is shared by every test in the run. */
    private static final AtomicInteger SEQ = new AtomicInteger(2000);

    private static final byte[] PDF_BYTES = "%PDF-1.7\n%test".getBytes(StandardCharsets.US_ASCII);

    @Autowired private MockMvc mvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private AttachmentRepository attachmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Student owner;
    private Student other;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        owner = student("Nimal Perera");
        other = student("Kamal Silva");
        ticket = openTicket(owner);
    }

    private Student student(String name) {
        int n = SEQ.incrementAndGet();
        Student s = new Student();
        s.setStudentId("IT" + (28000000 + n));
        s.setFullName(name);
        s.setEmail("f2student" + n + "@my.sliit.lk");
        s.setPassword(passwordEncoder.encode("Secret123"));
        s.setDepartment("Faculty of Computing");
        return studentRepository.save(s);
    }

    private Ticket openTicket(Student student) {
        Ticket t = new Ticket();
        t.setStudentId(student.getId());
        t.setSubject("Can't submit my assignment");
        t.setDescription("The upload page times out.");
        t.setCategory("Network");
        t.setPriority(TicketPriority.MEDIUM);
        t.setStatus(TicketStatus.OPEN);
        return ticketRepository.save(t);
    }

    private Attachment attach(Ticket t, String fileName) {
        Attachment a = new Attachment();
        a.setTicketId(t.getId());
        a.setFileName(fileName);
        a.setFileType("application/pdf");
        a.setFileSize((long) PDF_BYTES.length);
        a.setData(PDF_BYTES);
        a.setUploadedByUserId(t.getStudentId());
        return attachmentRepository.save(a);
    }

    @Test
    @DisplayName("The owner lists their ticket's files as metadata, with no file bytes in the JSON")
    void ownerListsMetadata() throws Exception {
        Attachment a = attach(ticket, "receipt.pdf");

        mvc.perform(get("/api/tickets/" + ticket.getId() + "/attachments")
                        .with(user(owner.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(a.getId()))
                .andExpect(jsonPath("$[0].ticketId").value(ticket.getId()))
                .andExpect(jsonPath("$[0].fileName").value("receipt.pdf"))
                .andExpect(jsonPath("$[0].fileType").value("application/pdf"))
                .andExpect(jsonPath("$[0].fileSize").value(PDF_BYTES.length))
                .andExpect(jsonPath("$[0].uploadedByUserId").value(owner.getId()))
                .andExpect(jsonPath("$[0].kind").value("SUBMISSION"))
                .andExpect(jsonPath("$[0].data").doesNotExist());
    }

    // 404, not 403: a 403 would confirm that ticket id exists.
    @Test
    @DisplayName("Another student listing the files gets 404")
    void otherStudentGets404() throws Exception {
        attach(ticket, "receipt.pdf");

        mvc.perform(get("/api/tickets/" + ticket.getId() + "/attachments")
                        .with(user(other.getEmail()).roles("STUDENT")))
                .andExpect(status().isNotFound());
    }

    // ---- Download header (F2-N4) ----

    // Why this test exists: the header was built as "inline; filename=\"" +
    // name + "\"", so a quote in the name ended the value early.
    @Test
    @DisplayName("Downloading quote\"d.pdf gives a safely encoded attachment header")
    void quotedNameIsEncoded() throws Exception {
        Attachment a = attach(ticket, "quote\"d.pdf");

        mvc.perform(get("/api/tickets/" + ticket.getId() + "/attachments/" + a.getId())
                        .with(user(owner.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''")))
                .andExpect(header().string("Content-Disposition", not(containsString("\"d.pdf\""))))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().bytes(PDF_BYTES));
    }

    @Test
    @DisplayName("A non-English file name is percent-encoded as UTF-8")
    void nonEnglishNameIsEncoded() throws Exception {
        Attachment a = attach(ticket, "රිසිට්.pdf");   // Sinhala "receipt"

        mvc.perform(get("/api/tickets/" + ticket.getId() + "/attachments/" + a.getId())
                        .with(user(owner.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("filename*=UTF-8''%E0%B6%BB")));
    }

    @Test
    @DisplayName("Another student downloading the file gets 404")
    void otherStudentCannotDownload() throws Exception {
        Attachment a = attach(ticket, "receipt.pdf");

        mvc.perform(get("/api/tickets/" + ticket.getId() + "/attachments/" + a.getId())
                        .with(user(other.getEmail()).roles("STUDENT")))
                .andExpect(status().isNotFound());
    }
}
