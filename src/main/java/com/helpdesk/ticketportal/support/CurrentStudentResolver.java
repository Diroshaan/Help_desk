package com.helpdesk.ticketportal.support;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Singleton (container-managed): the one shared object every F3 controller uses to find
 * the signed-in student. Spring creates exactly one instance at start-up and injects that
 * same instance everywhere, so the rule "the student comes from the session, never from
 * the request" lives in one place instead of a private copy in each controller.
 *
 * The scope is written out on purpose: singleton is Spring's default, but naming it makes
 * the design decision visible. It is safe to share because it holds no per-request state;
 * the only field is another singleton (StudentService), and each call works only on its
 * own Authentication argument.
 *
 * Spring's singleton is used instead of a hand-written one (private constructor and a
 * static getInstance()), because a static instance couldn't receive StudentService by
 * constructor injection and couldn't be replaced with a mock in tests.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
public class CurrentStudentResolver {

    private final StudentService studentService;

    @Autowired
    public CurrentStudentResolver(StudentService studentService) {
        this.studentService = studentService;
    }

    /**
     * Database id of the signed-in student. An officer or admin, or an account that no
     * longer exists, gets 404 (not 403), the same answer as before the refactor.
     */
    public Long currentStudentId(Authentication authentication) {
        return studentService.findByEmail(authentication.getName())
                .map(Student::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in student not found"));
    }
}
