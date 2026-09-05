package com.helpdesk.profile.service;

import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.profile.dto.ProfileUpdateRequest;
import com.helpdesk.profile.dto.RegistrationRequest;
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
    //
    // Takes a RegistrationRequest, not the Student entity itself (which this
    // method originally did) - see the comment on RegistrationRequest for the
    // main reason (the password complexity rule needs the raw password, which
    // only exists here, before hashing).
    public Student register(RegistrationRequest request) {
        if (studentRepository.existsByStudentId(request.getStudentId())) {
            throw new DuplicateResourceException("Student ID already registered");
        }
        if (studentRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered");
        }

        Student student = new Student();
        student.setStudentId(request.getStudentId());
        student.setFullName(request.getFullName());
        student.setEmail(request.getEmail());
        student.setDepartment(request.getDepartment());
        student.setContactNumber(request.getContactNumber());
        student.setProfilePictureUrl(request.getProfilePictureUrl());

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
        //
        // Defence in depth: RegistrationRequest is now the primary control here -
        // it has no id/active/role fields at all, so there's nothing on the wire
        // for a client to send that would reach this method. These three lines
        // (and the "role" one below) are the backstop for the day this endpoint
        // gets "simplified" back to binding a Student straight off the request -
        // exactly what it originally did - and silently reintroduces the
        // vulnerability described above. Keep them even though they're currently
        // redundant with the DTO; that redundancy is the point.
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
        student.setPassword(passwordEncoder.encode(request.getPassword()));
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
    //
    // PARTIAL update semantics: this endpoint is called with two genuinely
    // different, non-overlapping subsets of these six fields - "Save changes"
    // on the frontend sends fullName/phone/department but not the preference
    // booleans or the picture URL, and "Save preferences" sends only preference
    // fields. Each null on updatedDetails therefore means "the caller isn't
    // touching this field", not "clear it" - so every setter below is guarded
    // by a null check, and existing's current value is left alone when the
    // request didn't include one. Without these guards this method used to
    // overwrite every field unconditionally: saving your name would silently
    // wipe your profile picture URL and switch both notification preferences
    // off, because the fields "Save changes" doesn't send would deserialize as
    // null (or, before ProfileUpdateRequest's two booleans were changed from
    // primitive to Boolean, as a silent false) and get written straight over
    // whatever was already saved.
    public Student updateProfile(Long id, ProfileUpdateRequest updatedDetails) {
        Student existing = studentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        if (updatedDetails.getFullName() != null) {
            existing.setFullName(updatedDetails.getFullName());
        }
        if (updatedDetails.getDepartment() != null) {
            existing.setDepartment(updatedDetails.getDepartment());
        }
        if (updatedDetails.getContactNumber() != null) {
            existing.setContactNumber(updatedDetails.getContactNumber());
        }
        if (updatedDetails.getProfilePictureUrl() != null) {
            existing.setProfilePictureUrl(updatedDetails.getProfilePictureUrl());
        }
        if (updatedDetails.isEmailNotificationsEnabled() != null) {
            existing.setEmailNotificationsEnabled(updatedDetails.isEmailNotificationsEnabled());
        }
        if (updatedDetails.isPortalNotificationsEnabled() != null) {
            existing.setPortalNotificationsEnabled(updatedDetails.isPortalNotificationsEnabled());
        }

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
