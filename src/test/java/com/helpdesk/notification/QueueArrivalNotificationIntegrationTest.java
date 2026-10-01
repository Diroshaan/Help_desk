package com.helpdesk.notification;

import com.helpdesk.common.reference.entity.Category;
import com.helpdesk.common.reference.entity.Department;
import com.helpdesk.common.reference.repository.CategoryRepository;
import com.helpdesk.common.reference.repository.DepartmentRepository;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.OfficerRepository;
import com.helpdesk.notification.entity.Notification;
import com.helpdesk.notification.repository.NotificationRepository;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.repository.StudentRepository;
import com.helpdesk.ticket.dto.TicketCreateRequest;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketPriority;
import com.helpdesk.ticket.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-04, end to end on H2: a student submits a ticket through the real
 * TicketService, and the officers of that ticket's department are alerted -
 * and nobody else is. Builds its own data (Team guide 6.2) instead of relying
 * on the dev seeders.
 */
@SpringBootTest
@ActiveProfiles("test")
class QueueArrivalNotificationIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger(7000);

    @Autowired private StudentRepository studentRepository;
    @Autowired private OfficerRepository officerRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private TicketService ticketService;
    @Autowired private PasswordEncoder passwordEncoder;

    private Student student;
    private Category category;
    private Department otherDepartment;

    @BeforeEach
    void setUp() {
        int n = SEQ.incrementAndGet();
        Student s = new Student();
        s.setStudentId("IT" + (27000000 + n));
        s.setFullName("Kavindi Silva");
        s.setEmail("kavindi" + n + "@my.sliit.lk");
        s.setPassword(passwordEncoder.encode("Secret123"));
        s.setDepartment("Faculty of Computing");
        student = studentRepository.save(s);

        category = categoryRepository.findSelectableWithDepartment().get(0);
        String ownCode = category.getDepartment().getCode();
        otherDepartment = departmentRepository.findAll().stream()
                .filter(d -> !d.getCode().equals(ownCode))
                .findFirst()
                .orElseThrow();
    }

    private Officer officerServing(Department department, String label) {
        int n = SEQ.incrementAndGet();
        Officer o = new Officer(label + n + "@helpdesk.local", passwordEncoder.encode("Officer123"),
                "OQ" + (40000000 + n), "Support Officer", "Officer " + label);
        o.setDepartments(Set.of(department));
        return officerRepository.save(o);
    }

    private Ticket submit() {
        TicketCreateRequest request = new TicketCreateRequest();
        request.setSubject("Cannot log in to the LMS");
        request.setDescription("It says my password is wrong after the reset.");
        request.setCategory(category.getName());
        request.setPriority(TicketPriority.HIGH);
        return ticketService.createTicket(student.getId(), request);
    }

    private List<Notification> inboxOf(Officer officer) {
        return notificationRepository.findTop50ByRecipientUserIdOrderByCreatedAtDescIdDesc(officer.getId());
    }

    @Test
    @DisplayName("An officer serving the ticket's department is alerted, with a link to the queue view")
    void officerOfTheDepartmentIsAlerted() {
        Officer own = officerServing(category.getDepartment(), "own");

        Ticket ticket = submit();

        List<Notification> inbox = inboxOf(own);
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).getTitle()).isEqualTo("New ticket in your queue");
        assertThat(inbox.get(0).getBody()).contains("Cannot log in to the LMS");
        assertThat(inbox.get(0).getLink()).isEqualTo("#/queue/" + ticket.getId());
    }

    @Test
    @DisplayName("Officers of other departments, suspended officers and the student hear nothing")
    void nobodyElseIsAlerted() {
        Officer elsewhere = officerServing(otherDepartment, "elsewhere");
        Officer suspended = officerServing(category.getDepartment(), "suspended");
        suspended.setActive(false);
        officerRepository.save(suspended);

        submit();

        assertThat(inboxOf(elsewhere)).isEmpty();
        assertThat(inboxOf(suspended)).isEmpty();
        assertThat(notificationRepository.findTop50ByRecipientUserIdOrderByCreatedAtDescIdDesc(student.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("An officer who switched portal alerts off gets no inbox entry (Strategy honours the preference)")
    void portalPreferenceIsHonoured() {
        Officer quiet = officerServing(category.getDepartment(), "quiet");
        quiet.setPortalNotificationsEnabled(false);
        officerRepository.save(quiet);

        submit();

        assertThat(inboxOf(quiet)).isEmpty();
    }
}
