package com.helpdesk.profile.service;

import com.helpdesk.auth.SessionRevoker;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.common.files.FileTypeDetector;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.settings.HelpdeskSettings;
import com.helpdesk.profile.dto.ProfileUpdateRequest;
import com.helpdesk.profile.dto.RegistrationRequest;
import com.helpdesk.profile.entity.ActivityType;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Business logic for student accounts: register, update profile, deactivate, avatar.
 * Each method is one transaction, so its lookups, saves and activity-log entry
 * all commit or roll back together.
 */
@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;

    // ends the account's live sessions on deactivation
    private final SessionRevoker sessionRevoker;

    @Autowired
    public StudentService(StudentRepository studentRepository,
                          PasswordEncoder passwordEncoder,
                          ActivityLogService activityLogService,
                          SessionRevoker sessionRevoker) {
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.activityLogService = activityLogService;
        this.sessionRevoker = sessionRevoker;
    }

    @Transactional
    public Student register(RegistrationRequest request) {
        // load the row (not existsBy) so we can tell active and deactivated accounts apart
        rejectIfAlreadyTaken(studentRepository.findByStudentId(request.getStudentId()), "This Student ID");
        rejectIfAlreadyTaken(studentRepository.findByEmail(request.getEmail()), "This email address");

        Student student = new Student();
        student.setStudentId(request.getStudentId());
        applyName(student, request.getFullName(), request.getGivenName(), request.getSurname());
        student.setEmail(request.getEmail());
        student.setDepartment(request.getDepartment());

        // "phones" wins over "phone" if both are sent
        if (request.getPhones() != null) {
            student.setContactNumbers(request.getPhones());
        } else {
            student.setPrimaryContactNumber(request.getContactNumber());
        }

        // Backstop against mass assignment: a non-null id would make save() overwrite
        // an existing account. The DTO has no id/active fields, but keep these anyway.
        student.setId(null);
        student.setActive(true);

        student.setPassword(passwordEncoder.encode(request.getPassword()));

        // Two requests can race past the checks above; the unique columns catch
        // that and GlobalExceptionHandler turns it into the same 409.
        Student saved = studentRepository.save(student);

        activityLogService.record(saved.getId(), ActivityType.ACCOUNT_CREATED,
                "Account created.");

        return saved;
    }

    /**
     * The message depends on the account's state. We never reactivate an old account
     * here: registration is public, so that would let anyone who knows a student's ID
     * and email take over their account and ticket history. Only an admin restores.
     */
    private void rejectIfAlreadyTaken(Optional<Student> existing, String label) {
        existing.ifPresent(student -> {
            if (student.isActive()) {
                throw new DuplicateResourceException(
                        label + " is already registered. Please log in instead.");
            }
            // removed accounts are final; only suspended ones can be restored
            if (student.isRemoved()) {
                throw new DuplicateResourceException(
                        label + " belongs to an account that was closed, so it cannot be registered again. "
                                + "Contact the help desk administrator if you need help.");
            }
            throw new DuplicateResourceException(
                    label + " belongs to an account that has been deactivated. "
                            + "Contact the help desk administrator to have it restored.");
        });
    }

    @Transactional(readOnly = true)
    public List<Student> findAll() {
        return studentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Student> findById(Long id) {
        return studentRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Student> findByEmail(String email) {
        return studentRepository.findByEmail(email);
    }

    /**
     * Partial update: "Save changes" and "Save preferences" each send only some
     * fields, so a null means "leave it alone", never "clear it".
     * Only fields that really changed are logged, so saving with no edits logs nothing.
     */
    @Transactional
    public Student updateProfile(Long id, ProfileUpdateRequest updatedDetails) {
        Student existing = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        // labels the student sees in their activity log
        List<String> changedDetails = new ArrayList<>();
        boolean preferencesChanged = false;

        // compare the full name before and after, whichever shape was sent
        if (updatedDetails.getFullName() != null
                || updatedDetails.getGivenName() != null
                || updatedDetails.getSurname() != null) {
            String before = existing.getFullName();
            applyName(existing, updatedDetails.getFullName(),
                    updatedDetails.getGivenName(), updatedDetails.getSurname());
            if (!Objects.equals(before, existing.getFullName())) {
                changedDetails.add("name");
            }
        }
        if (updatedDetails.getDepartment() != null) {
            if (!Objects.equals(updatedDetails.getDepartment(), existing.getDepartment())) {
                changedDetails.add("faculty");
            }
            existing.setDepartment(updatedDetails.getDepartment());
        }
        // copy first: the entity changes its list in place
        if (updatedDetails.getPhones() != null || updatedDetails.getContactNumber() != null) {
            List<String> before = new ArrayList<>(existing.getContactNumbers());
            if (updatedDetails.getPhones() != null) {
                existing.setContactNumbers(updatedDetails.getPhones());
            } else {
                existing.setPrimaryContactNumber(updatedDetails.getContactNumber());
            }
            if (!before.equals(existing.getContactNumbers())) {
                changedDetails.add("contact numbers");
            }
        }
        if (updatedDetails.isEmailNotificationsEnabled() != null) {
            if (updatedDetails.isEmailNotificationsEnabled() != existing.isEmailNotificationsEnabled()) {
                preferencesChanged = true;
            }
            existing.setEmailNotificationsEnabled(updatedDetails.isEmailNotificationsEnabled());
        }
        if (updatedDetails.isPortalNotificationsEnabled() != null) {
            if (updatedDetails.isPortalNotificationsEnabled() != existing.isPortalNotificationsEnabled()) {
                preferencesChanged = true;
            }
            existing.setPortalNotificationsEnabled(updatedDetails.isPortalNotificationsEnabled());
        }

        Student saved = studentRepository.save(existing);

        // separate entries, matching the two save buttons on the page
        if (!changedDetails.isEmpty()) {
            activityLogService.record(saved.getId(), ActivityType.PROFILE_UPDATED,
                    "Profile updated: " + String.join(", ", changedDetails) + ".");
        }
        if (preferencesChanged) {
            activityLogService.record(saved.getId(), ActivityType.PREFERENCES_UPDATED,
                    "Notification preferences updated.");
        }

        return saved;
    }

    /** Self-service account deletion. Soft delete: the row stays so tickets and history still resolve. */
    @Transactional
    public void deactivate(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        // "removed" (owner closed it), not "suspended", so an admin can't simply reactivate it
        student.markRemoved();
        studentRepository.save(student);

        // The active flag only blocks new logins, so also end any open sessions
        // (after the save, so a failed save doesn't sign them out).
        sessionRevoker.revokeAllSessionsFor(student.getEmail());

        activityLogService.record(student.getId(), ActivityType.ACCOUNT_DEACTIVATED,
                "Account closed by the account holder.");
    }

    /** The name parts win over fullName; fullName is only split when no parts were sent. */
    private void applyName(Student student, String fullName, String givenName, String surname) {
        if (givenName != null || surname != null) {
            if (givenName != null) {
                student.setGivenName(givenName);
            }
            if (surname != null) {
                student.setSurname(surname);
            }
        } else if (fullName != null) {
            student.setFullName(fullName);
        }
    }

    // allow-list, so anything we didn't think of is rejected
    private static final Set<String> ALLOWED_AVATAR_TYPES = HelpdeskSettings.getInstance().getAvatarTypes();

    private static final long MAX_AVATAR_BYTES = HelpdeskSettings.getInstance().getMaxAvatarBytes();

    /**
     * Saves a new profile picture. The declared type is client-supplied, so we also
     * check the file's first bytes and store the detected type, not the declared one.
     */
    @Transactional
    public Student updateAvatar(Long id, MultipartFile file) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose an image to upload.");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new IllegalArgumentException("That image is larger than 2MB.");
        }

        String declaredType = file.getContentType();
        if (declaredType == null || !ALLOWED_AVATAR_TYPES.contains(declaredType.toLowerCase())) {
            throw new IllegalArgumentException("Profile pictures must be a JPEG, PNG or WebP image.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("We could not read that file. Please try again.", e);
        }

        // what the bytes really are, whatever the upload claimed
        String detectedType = detectImageType(bytes);
        if (detectedType == null) {
            throw new IllegalArgumentException(
                    "That file is not a valid image, whatever its name says.");
        }

        student.setProfilePicture(bytes);
        student.setProfilePictureType(detectedType);

        // Relative URL so it works on any host. ?v= changes on every upload so the
        // browser doesn't keep showing the cached old picture.
        student.setProfilePictureUrl(
                "/api/students/" + student.getId() + "/avatar?v=" + System.currentTimeMillis());

        Student saved = studentRepository.save(student);
        activityLogService.record(saved.getId(), ActivityType.PROFILE_UPDATED,
                "Profile picture updated.");
        return saved;
    }

    /** MIME type from the file's magic bytes (JPEG, PNG or WebP), or null. */
    private String detectImageType(byte[] bytes) {
        return FileTypeDetector.detect(bytes, ALLOWED_AVATAR_TYPES).orElse(null);
    }

    @Transactional(readOnly = true)
    public Student getWithAvatar(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
    }
}
