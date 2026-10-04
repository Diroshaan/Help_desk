package com.helpdesk.ticketportal;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.repository.StudentRepository;
import com.helpdesk.ticket.controller.StudentTicketSearchController;
import com.helpdesk.ticketportal.controller.ArchivedTicketListController;
import com.helpdesk.ticketportal.controller.BookmarkController;
import com.helpdesk.ticketportal.controller.BookmarkFolderController;
import com.helpdesk.ticketportal.controller.FeedbackController;
import com.helpdesk.ticketportal.controller.TicketArchiveController;
import com.helpdesk.ticketportal.controller.TicketResolutionController;
import com.helpdesk.ticketportal.support.CurrentStudentResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Singleton: one shared CurrentStudentResolver for every F3 controller, same behaviour as before. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CurrentStudentResolverSingletonTest {

    private static final AtomicInteger SEQ = new AtomicInteger(14000);

    @Autowired private ConfigurableApplicationContext context;
    @Autowired private CurrentStudentResolver resolver;
    @Autowired private MockMvc mvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Spring creates exactly one instance, registered with singleton scope")
    void oneInstance() {
        assertThat(context.getBean(CurrentStudentResolver.class))
                .isSameAs(context.getBean(CurrentStudentResolver.class))
                .isSameAs(resolver);
        assertThat(context.getBeanFactory().getBeanDefinition("currentStudentResolver").isSingleton()).isTrue();
        assertThat(context.getBeanNamesForType(CurrentStudentResolver.class)).hasSize(1);
    }

    @Test
    @DisplayName("All seven F3 controllers share that same instance")
    void controllersShareIt() {
        List<Class<?>> controllers = List.of(
                BookmarkController.class, BookmarkFolderController.class, FeedbackController.class,
                TicketArchiveController.class, ArchivedTicketListController.class,
                TicketResolutionController.class, StudentTicketSearchController.class);
        for (Class<?> type : controllers) {
            Object shared = ReflectionTestUtils.getField(context.getBean(type), "currentStudent");
            assertThat(shared).as(type.getSimpleName()).isSameAs(resolver);
        }
    }

    @Test
    @DisplayName("Returns the signed-in student's id; anyone else is 'not found'")
    void resolvesFromSession() {
        Student student = saveStudent();
        assertThat(resolver.currentStudentId(auth(student.getEmail()))).isEqualTo(student.getId());
        assertThatThrownBy(() -> resolver.currentStudentId(auth("nobody@sliit.lk")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Logged-in student not found");
    }

    @Test
    @DisplayName("A non-student on an F3 endpoint still gets 404, as before the refactor")
    void nonStudentIs404() throws Exception {
        mvc.perform(get("/api/bookmarks").with(user("officer.x@sliit.lk").roles("OFFICER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/tickets/archived").with(user("officer.x@sliit.lk").roles("OFFICER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A student still reaches F3 endpoints through the shared resolver")
    void studentStillWorks() throws Exception {
        Student student = saveStudent();
        mvc.perform(get("/api/bookmark-folders").with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/tickets/search").with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk());
    }

    private static UsernamePasswordAuthenticationToken auth(String email) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of());
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
}
