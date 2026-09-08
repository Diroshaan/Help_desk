package com.helpdesk.profile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.helpdesk.profile.entity.Student;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response body for every endpoint in StudentController that returns a student.
 *
 * Why this exists: until now those endpoints returned the Student ENTITY
 * directly, and Jackson serialised whatever fields it happened to have. That is
 * the mirror image of the problem RegistrationRequest and ProfileUpdateRequest
 * already solved on the way in, and it costs three things on the way out.
 *
 *  1. The entity's field list silently became the public API. Anybody adding a
 *     field to Student - a password reset token, an internal note, an admin
 *     flag - published it to every caller of GET /api/students/me without
 *     touching an endpoint or noticing they had changed the contract.
 *
 *  2. The password hash was protected by exactly one annotation. Student.password
 *     carries @JsonProperty(access = WRITE_ONLY), and that single line was the
 *     only thing standing between a bcrypt hash and the wire. Delete it by
 *     accident and every profile fetch leaks it, with nothing failing to say so.
 *     Now the protection is structural instead: this class has no password field,
 *     so there is nothing to leak and nothing to delete by mistake.
 *
 *  3. It would not have survived the activity log. That feature (F1's
 *     "Dashboard & Activity View") adds a @OneToMany to Student. Serialising the
 *     entity would then either drag every log row into every profile response,
 *     or fail outright on a lazy-loading proxy once the transaction has closed -
 *     the well-known LazyInitializationException. A response DTO decides what
 *     gets loaded and sent, so neither can happen.
 *
 * The rule this sets: what leaves the application is listed here, explicitly. A
 * new field on Student appears in the API only when somebody adds it to this
 * class on purpose.
 *
 * On the wire format: the field names below are exactly the ones the frontend
 * already reads (see useSession.jsx, Profile.jsx, DeleteAccount.jsx), so
 * introducing this class changes no JSON and needs no frontend change.
 *
 * On shape: this is a plain class with final fields rather than a Java record.
 * A record would be shorter and would work, but the two sibling DTOs in this
 * package are plain classes with explicit getters, and a reviewer comparing the
 * three should see one pattern rather than two. The fields are final and there
 * are no setters, because a response is built once and then written out - there
 * is no point in the request where mutating it would be correct.
 */
public class StudentResponse {

    private final Long id;
    private final String studentId;
    private final String fullName;
    private final String email;

    /**
     * Serialised as "phone", matching RegistrationRequest and
     * ProfileUpdateRequest, because that is the name the frontend uses in every
     * payload it sends and reads.
     *
     * This annotation is doing real work, not decoration: Jackson names a
     * property after the getter, so without it this would go out as
     * "contactNumber" and the frontend's `student.phone` would read undefined -
     * the exact bug that left the phone box on the profile page empty even when
     * a number was stored. The Java-side name stays contactNumber to match
     * Student and the two request DTOs.
     */
    @JsonProperty("phone")
    private final String contactNumber;

    private final String department;
    private final String profilePictureUrl;

    /**
     * Included deliberately, and safe to include: it is the caller's own role on
     * their own account, or a role an Officer/Admin is entitled to see on the
     * staff listing. Knowing your own role grants nothing - authorisation is
     * decided server-side from the session, never from a value the client holds.
     * F6's admin screens will need it to show who is staff.
     *
     * Note the asymmetry with the request side, where role is deliberately NOT
     * accepted (see StudentService.register): readable out, never writable in.
     */
    private final String role;

    private final boolean emailNotificationsEnabled;
    private final boolean portalNotificationsEnabled;

    /** The US-02 soft-delete flag. The profile page renders it as Active/Suspended. */
    private final boolean active;

    /** Supports a "member since" line on the profile without another round trip. */
    private final LocalDateTime createdAt;

    public StudentResponse(Long id, String studentId, String fullName, String email,
                           String contactNumber, String department, String profilePictureUrl,
                           String role, boolean emailNotificationsEnabled,
                           boolean portalNotificationsEnabled, boolean active,
                           LocalDateTime createdAt) {
        this.id = id;
        this.studentId = studentId;
        this.fullName = fullName;
        this.email = email;
        this.contactNumber = contactNumber;
        this.department = department;
        this.profilePictureUrl = profilePictureUrl;
        this.role = role;
        this.emailNotificationsEnabled = emailNotificationsEnabled;
        this.portalNotificationsEnabled = portalNotificationsEnabled;
        this.active = active;
        this.createdAt = createdAt;
    }

    /**
     * Builds the response for one student.
     *
     * This mapping lives on the DTO rather than in StudentService on purpose.
     * The service layer holds business rules and returns domain objects; how
     * those are presented over HTTP is a concern of the web layer. Keeping the
     * service returning Student means it stays reusable by anything that is not
     * a REST controller - a scheduled job, another service, a test - without
     * dragging a JSON shape along with it.
     */
    public static StudentResponse from(Student student) {
        return new StudentResponse(
                student.getId(),
                student.getStudentId(),
                student.getFullName(),
                student.getEmail(),
                student.getContactNumber(),
                student.getDepartment(),
                student.getProfilePictureUrl(),
                student.getRole(),
                student.isEmailNotificationsEnabled(),
                student.isPortalNotificationsEnabled(),
                student.isActive(),
                student.getCreatedAt()
        );
    }

    /**
     * Convenience for the staff listing endpoint.
     *
     * Named fromAll rather than overloading from(...) deliberately: an
     * overloaded method used as a method reference (StudentResponse::from) can
     * become ambiguous to the compiler when the target type still has to be
     * inferred, as it does inside Optional.map(...). A distinct name removes
     * the question entirely and reads more clearly at the call site.
     */
    public static List<StudentResponse> fromAll(List<Student> students) {
        return students.stream().map(StudentResponse::from).toList();
    }

    // --- Getters only. Jackson serialises from these; nothing mutates a response. ---

    public Long getId() {
        return id;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public String getDepartment() {
        return department;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public String getRole() {
        return role;
    }

    public boolean isEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }

    public boolean isPortalNotificationsEnabled() {
        return portalNotificationsEnabled;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
