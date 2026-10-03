package com.helpdesk.ticketportal;

import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.repository.StudentRepository;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketPriority;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticketportal.entity.Bookmark;
import com.helpdesk.ticketportal.entity.Feedback;
import com.helpdesk.ticketportal.repository.BookmarkRepository;
import com.helpdesk.ticketportal.repository.FeedbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #42: the database itself refuses a second bookmark of the same ticket by
 * the same student, and a second feedback on the same ticket. These go
 * straight to the repositories - past the services' existsBy... checks - to
 * show the constraint holds even when that check is raced.
 */
@SpringBootTest
@ActiveProfiles("test")
class TicketPortalUniqueConstraintsIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger(9000);

    @Autowired private StudentRepository studentRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private BookmarkRepository bookmarkRepository;
    @Autowired private FeedbackRepository feedbackRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Student student;
    private Ticket ticket;

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

        Ticket t = new Ticket();
        t.setStudentId(student.getId());
        t.setSubject("Exam timetable clash");
        t.setDescription("Two exams at the same time on Monday.");
        t.setCategory("Network");
        t.setPriority(TicketPriority.HIGH);
        t.setStatus(TicketStatus.RESOLVED);
        ticket = ticketRepository.save(t);
    }

    private Bookmark bookmark() {
        Bookmark b = new Bookmark();
        b.setStudentId(student.getId());
        b.setTicketId(ticket.getId());
        return b;
    }

    private Feedback feedback(int rating) {
        Feedback f = new Feedback();
        f.setStudentId(student.getId());
        f.setTicketId(ticket.getId());
        f.setRating(rating);
        return f;
    }

    @Test
    @DisplayName("A second bookmark of the same ticket by the same student is refused by the database")
    void duplicateBookmarkIsRefused() {
        bookmarkRepository.saveAndFlush(bookmark());

        assertThatThrownBy(() -> bookmarkRepository.saveAndFlush(bookmark()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("A second feedback on the same ticket is refused by the database")
    void duplicateFeedbackIsRefused() {
        feedbackRepository.saveAndFlush(feedback(5));

        assertThatThrownBy(() -> feedbackRepository.saveAndFlush(feedback(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
