package com.helpdesk.auth;

import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bridges your Student entity to Spring Security's authentication system.
 * Spring Security doesn't know what a "Student" is - this class translates
 * one into the generic UserDetails shape Security expects, so it can check
 * passwords and know what role/authority the account has.
 *
 * Because this is the only UserDetailsService bean in the app, Spring Boot
 * auto-wires it into the authentication process automatically - you don't
 * need to reference this class anywhere else.
 */
@Service
public class StudentUserDetailsService implements UserDetailsService {

    private final StudentRepository studentRepository;

    @Autowired
    public StudentUserDetailsService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Student student = studentRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account found for email: " + email));

        // "ROLE_" prefix is a Spring Security convention - hasRole("STUDENT") internally
        // checks for the authority "ROLE_STUDENT". This is what lets you later write
        // rules like .requestMatchers("/api/queue/**").hasRole("OFFICER") once F4 exists.
        return User.builder()
                .username(student.getEmail())
                .password(student.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + student.getRole())))
                .disabled(!student.isActive())
                .build();
    }
}
