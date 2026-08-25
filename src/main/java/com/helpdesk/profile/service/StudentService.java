package com.helpdesk.profile.service;

import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.profile.dto.ProfileUpdateRequest;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * Business logic for Student profiles (US-01, US-02, US-03, US-06).
 * Controllers should stay thin and call methods here - this is where
 * validation rules, uniqueness checks, and business decisions live.
 */
@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public StudentService(StudentRepository studentRepository, PasswordEncoder passwordEncoder) {
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Create (US-03: register a new student account)
    public Student register(Student student) {
        if (studentRepository.existsByStudentId(student.getStudentId())) {
            throw new DuplicateResourceException("Student ID already registered");
        }
        if (studentRepository.existsByEmail(student.getEmail())) {
            throw new DuplicateResourceException("Email already registered");
        }

        // Never trust a client-supplied primary key.
        //
        // Why this matters: @RequestBody binds EVERY field of Student straight from
        // the JSON, "id" included - and Spring Data JPA decides insert-vs-update
        // purely by asking "is the id null?" (SimpleJpaRepository.save() calls
        // persist() when it is, merge() when it isn't). So a request like
        //   POST /api/students { "id": 1, "studentId": "NEW001", "email": "x@y.com", ... }
        // passes BOTH duplicate checks above (that studentId and email genuinely
        // aren't taken), then calls merge() - which loads student #1 and overwrites
        // that row with the caller's details, including their password hash. The
        // original owner loses their account and the caller can log in as them.
        // Clearing the id here forces a genuine insert every time.
        //
        // "active" gets the same treatment for the same reason: it's the soft-delete
        // flag behind US-02, not something a registration request has any business
        // setting. This is the same mass-assignment class of bug as the "role" field
        // handled just below.
        student.setId(null);
        student.setActive(true);

        // Force every self-registered account to be a STUDENT, no matter what the
        // caller put in the "role" field of the request body.
        //
        // Why this matters: unlike "password" (which is marked WRITE_ONLY so it's
        // rejected from responses but still readable from requests), "role" has no
        // such protection on the Student entity - the JSON body is bound straight
        // onto the entity's fields. That means a request like
        //   POST /api/students { ..., "role": "ADMIN" }
        // would otherwise let anyone grant themselves Officer/Admin privileges just
        // by adding one extra field to a public, unauthenticated endpoint. This is
        // a classic "mass assignment" vulnerability. Setting the role here, AFTER
        // validation and BEFORE save, means whatever the client sent is discarded
        // and self-registration can only ever produce a STUDENT account. Creating
        // Officer/Admin accounts will need its own, access-controlled path (F6).
        student.setRole("STUDENT");

        // Never store the plain-text password - hash it before saving.
        student.setPassword(passwordEncoder.encode(student.getPassword()));
        return studentRepository.save(student);
    }

    // Read
    public List<Student> findAll() {
        return studentRepository.findAll();
    }

    public Optional<Student> findById(Long id) {
        return studentRepository.findById(id);
    }

    // Used by GET /api/students/me - looks a student up by the email they
    // logged in with (Authentication.getName()), rather than by a numeric id
    // the frontend would otherwise have no way to know.
    public Optional<Student> findByEmail(String email) {
        return studentRepository.findByEmail(email);
    }

    // Update (US-01: edit profile details and notification preferences)
    //
    // Takes a ProfileUpdateRequest rather than a Student entity - see the
    // comment on that class for why. In short: this method only ever needs
    // to touch the six fields listed below, so accepting anything wider
    // (the full Student, with its password/email/role/studentId) would let
    // callers send fields that either shouldn't be editable here at all, or
    // that would be silently ignored - both confusing outcomes. Sticking to
    // a purpose-built request type makes "what this endpoint can change"
    // obvious just from its method signature.
    public Student updateProfile(Long id, ProfileUpdateRequest updatedDetails) {
        Student existing = studentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        existing.setFullName(updatedDetails.getFullName());
        existing.setDepartment(updatedDetails.getDepartment());
        existing.setContactNumber(updatedDetails.getContactNumber());
        existing.setProfilePictureUrl(updatedDetails.getProfilePictureUrl());
        existing.setEmailNotificationsEnabled(updatedDetails.isEmailNotificationsEnabled());
        existing.setPortalNotificationsEnabled(updatedDetails.isPortalNotificationsEnabled());

        return studentRepository.save(existing);
    }

    // Delete (US-02: self-service account deletion - soft delete, not a hard DB delete)
    public void deactivate(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        student.setActive(false);
        studentRepository.save(student);
    }
}
