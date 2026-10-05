package com.helpdesk.auth;

import com.helpdesk.common.user.entity.AppUser;
import com.helpdesk.common.user.repository.AppUserRepository;
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
 * Loads any account (student, officer or admin) for Spring Security login. Looks up AppUser,
 * so one code path covers all three types. The name is older than the officer/admin accounts.
 */
@Service
public class StudentUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    // Only for logging in with a Student ID, which exists on students only.
    private final StudentRepository studentRepository;

    @Autowired
    public StudentUserDetailsService(AppUserRepository appUserRepository,
                                     StudentRepository studentRepository) {
        this.appUserRepository = appUserRepository;
        this.studentRepository = studentRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        // Email first (every account has one), then Student ID. or() is lazy, so the second
        // query only runs if the first finds nothing.
        AppUser user = appUserRepository.findByEmail(login)
                .or(() -> studentRepository.findByStudentId(login))
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found for '" + login + "'"));

        // The principal name is always the email, even after a Student ID login, because
        // ownership checks compare Authentication.getName() with the account email.
        return User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                // hasRole("OFFICER") looks for the authority "ROLE_OFFICER"
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                // inactive accounts get DisabledException; AuthController shows a clear message for it
                .disabled(!user.isActive())
                .build();
    }
}
