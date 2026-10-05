package com.helpdesk.profile.controller;

import com.helpdesk.profile.dto.ActivityLogResponse;
import com.helpdesk.profile.dto.ProfileUpdateRequest;
import com.helpdesk.profile.dto.RegistrationRequest;
import com.helpdesk.profile.dto.StudentResponse;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.service.ActivityLogService;
import com.helpdesk.profile.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

/**
 * Student profile endpoints: register, view, edit, deactivate and avatar.
 * SecurityConfig only checks that someone is signed in, so the {id} endpoints check
 * ownership themselves with isOwnProfile(). Always returns StudentResponse, never the entity.
 */
@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;
    private final ActivityLogService activityLogService;

    @Autowired
    public StudentController(StudentService studentService,
                             ActivityLogService activityLogService) {
        this.studentService = studentService;
        this.activityLogService = activityLogService;
    }

    // Public, since you can't sign in before the account exists.
    @PostMapping
    public ResponseEntity<StudentResponse> register(@Valid @RequestBody RegistrationRequest request) {
        Student saved = studentService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(StudentResponse.from(saved));
    }

    // Officers and admins only (role rule in SecurityConfig, not here).
    @GetMapping
    public List<StudentResponse> findAll() {
        return StudentResponse.fromAll(studentService.findAll());
    }

    // The signed-in student, looked up by session email, so the frontend needs no id.
    // 403 if the session has no matching student row any more.
    @GetMapping("/me")
    public ResponseEntity<StudentResponse> getCurrentStudent(Authentication authentication) {
        return studentService.findByEmail(authentication.getName())
                .map(this::withActivity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    // Staff can view any student; a student only their own profile.
    @GetMapping("/{id}")
    public ResponseEntity<StudentResponse> findById(@PathVariable Long id, Authentication authentication) {
        if (!isOfficerOrAdmin(authentication) && !isOwnProfile(id, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return studentService.findById(id)
                .map(this::withActivity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Owner only. Returns the refreshed activity log so the new entry shows straight away.
    @PutMapping("/{id}")
    public ResponseEntity<StudentResponse> updateProfile(@PathVariable Long id,
                                                         @Valid @RequestBody ProfileUpdateRequest updatedDetails,
                                                         Authentication authentication) {
        if (!isOwnProfile(id, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(withActivity(studentService.updateProfile(id, updatedDetails)));
    }

    // Owner only. Soft delete: the row stays, only the active flag changes.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id, Authentication authentication,
                                            HttpServletRequest request) {
        if (!isOwnProfile(id, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        studentService.deactivate(id);

        // The flag only blocks future logins, so end the current session too.
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        return ResponseEntity.noContent().build();
    }

    /** Adds recent activity; only used where a single profile is shown. */
    private StudentResponse withActivity(Student student) {
        return StudentResponse.withActivity(
                student,
                ActivityLogResponse.fromAll(activityLogService.recentFor(student.getId()))
        );
    }

    /**
     * Stops IDOR: the student at {id} must have the session's email.
     * An unknown id also gives false (403), so ids can't be probed.
     */
    private boolean isOwnProfile(Long id, Authentication authentication) {
        return studentService.findById(id)
                .map(student -> student.getEmail().equals(authentication.getName()))
                .orElse(false);
    }

    private boolean isOfficerOrAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_OFFICER")
                        || authority.getAuthority().equals("ROLE_ADMIN"));
    }

    /** Owner only. Multipart upload; size is capped by the multipart limits in application.properties. */
    @PostMapping("/{id}/avatar")
    public ResponseEntity<StudentResponse> uploadAvatar(@PathVariable Long id,
                                                          @RequestParam("file") MultipartFile file,
                                                          Authentication authentication) {
        if (!isOwnProfile(id, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Student updated = studentService.updateAvatar(id, file);
        return ResponseEntity.ok(withActivity(updated));
    }

    /**
     * Any signed-in user can fetch it (it's shown next to the name, like a display name).
     * 404 when there's no picture so the frontend falls back to initials.
     */
    @GetMapping("/{id}/avatar")
    public ResponseEntity<byte[]> avatar(@PathVariable Long id) {
        Student student = studentService.getWithAvatar(id);
        byte[] image = student.getProfilePicture();

        if (image == null || image.length == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        student.getProfilePictureType() == null
                                ? MediaType.IMAGE_JPEG_VALUE
                                : student.getProfilePictureType()))
                // private so shared proxies don't cache it; a new upload changes the ?v= in the URL
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .body(image);
    }
}
