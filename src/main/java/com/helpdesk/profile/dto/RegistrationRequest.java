package com.helpdesk.profile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.helpdesk.common.validation.ValidationRules;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for POST /api/students - new account registration (US-03).
 *
 * Why this exists instead of continuing to bind straight onto the Student
 * entity (which the endpoint did originally): the password complexity rule
 * (at least 8 characters, an upper-case letter, a lower-case letter, and a
 * digit) can only ever be checked against the RAW password the client typed
 * - and this is the one and only place that raw value exists. StudentService
 * hashes it immediately after this validation passes and never stores the
 * plaintext again.
 *
 * That's specifically why this rule can't live on Student.password itself:
 * Student is also the entity StudentService.updateProfile()/.deactivate() load
 * from the database and save again on every profile edit or deactivation, and
 * JPA/Hibernate re-runs Bean Validation on every one of those saves - by then
 * the field holds a bcrypt hash, not the original password, and a hash has no
 * reason to satisfy an "upper-case + lower-case + digit" rule meant for
 * human-typed passwords. Putting the rule on the entity would make ordinary
 * profile edits fail validation against a value nobody ever typed. Putting it
 * here instead means it's checked exactly once, at the only moment the raw
 * password actually exists.
 *
 * The same reasoning is why this DTO exists at all rather than just adding
 * the field to Student's registration binding: everything else needed for
 * validating a NEW account (studentId format, email shape, required fields)
 * already lives safely on Student because those fields don't change shape
 * after registration - password is the one exception, so it needs its own
 * request type to hold the rule that only makes sense before hashing.
 *
 *
 * WHY THE LENGTH LIMITS ARE REPEATED HERE AS WELL AS ON THE ENTITY
 * ----------------------------------------------------------------
 * The paragraph above says the other rules can "live safely on Student". For
 * FORMAT rules that is true. For LENGTH rules it turned out not to be, and the
 * difference is WHEN each one runs.
 *
 * A rule on this DTO runs at the controller, through @Valid, before anything
 * else happens. Its failure becomes a MethodArgumentNotValidException, and
 * GlobalExceptionHandler turns that into a clean 400 carrying only the
 * message written below - "Full name must be 120 characters or fewer".
 *
 * A rule on the entity runs much later, inside Hibernate, at the moment the
 * row is about to be written. It still fails - the bad value never reaches
 * the database - but it fails as a ConstraintViolationException thrown from
 * the middle of a save, and its default text is Hibernate's own:
 *
 *   "Validation failed for classes [com.helpdesk.profile.entity.Student]
 *    during persist time for groups [jakarta.validation.groups.Default, ]
 *    List of constraint violations:[ ConstraintViolationImpl{..."
 *
 * That is what a student typing a very long name used to see in the red box
 * on the registration form. It was found by testing, not by reading.
 *
 * So each @Size below mirrors the entity's column length exactly. The entity
 * keeps its own copy as the last line of defence for any path that does not
 * come through this DTO; this copy is what gives the user a sentence they can
 * act on. The numbers must stay in step with Student and AppUser - if a
 * column is widened, both places change together.
 */
public class RegistrationRequest {

    @NotBlank(message = "Student ID is required")
    @Pattern(regexp = "^[A-Z]{2}\\d{8}$", message = "Student ID must be two letters followed by eight digits, e.g. IT25101580")
    private String studentId;

    // THE NAME, in either of two shapes.
    //
    // The specification now has the name stored as given name + surname (see
    // Student.givenName). A client may send those two parts directly, or send
    // one "fullName" as the current registration page does, which the service
    // splits. Accepting both means the database change needed no frontend
    // change on the day it landed, and the form can move to two boxes whenever
    // it is redesigned.
    //
    // None of the three carries @NotBlank on its own, because each is optional
    // provided the other shape is present. The "at least one" rule is
    // isNameProvided() below.
    @Size(max = 120, message = "Full name must be 120 characters or fewer")
    private String fullName;

    @Size(max = 120, message = "Given name must be 120 characters or fewer")
    private String givenName;

    @Size(max = 120, message = "Surname must be 120 characters or fewer")
    private String surname;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @Size(max = 120, message = "Email must be 120 characters or fewer")
    private String email;

    // At least 8 characters, with an upper-case letter, a lower-case letter,
    // and a digit somewhere in it - see the class comment above for why this
    // lives here and not on Student.password.
    //
    // The rule itself now lives in ValidationRules, shared with password change,
    // so the two can never drift apart. The upper limit is new: see
    // ValidationRules.PASSWORD_MAX_LENGTH for why BCrypt makes it necessary.
    @NotBlank(message = "Password is required")
    @Pattern(regexp = ValidationRules.PASSWORD_REGEX, message = ValidationRules.PASSWORD_MESSAGE)
    @Size(max = ValidationRules.PASSWORD_MAX_LENGTH, message = ValidationRules.PASSWORD_LENGTH_MESSAGE)
    private String password;

    @NotBlank(message = "Department is required")
    @Size(max = 100, message = "Faculty must be 100 characters or fewer")
    private String department;

    // The registration form (frontend/src/pages/Register.jsx) sends this field
    // as "phone" in its JSON payload, not "contactNumber" - Jackson binds by
    // exact property name, so without @JsonProperty here, a request carrying
    // "phone" would silently fail to populate this field (Jackson ignores
    // unrecognised JSON properties by default rather than erroring), and the
    // student's phone number would just vanish with no validation failure to
    // reveal why. The Java-side name stays "contactNumber" to match Student's
    // and ProfileUpdateRequest's field of the same name.
    //
    // Still accepted, as the FIRST contact number, so the current single phone
    // box keeps working. The full list goes in "phones" below.
    @JsonProperty("phone")
    @Size(max = 30, message = "Phone number must be 30 characters or fewer")
    @Pattern(regexp = ValidationRules.PHONE_REGEX, message = ValidationRules.PHONE_MESSAGE)
    private String contactNumber;

    // Every contact number, in order - the multivalued attribute the
    // specification asks for. If both "phones" and "phone" are sent, "phones"
    // wins, because it is the more complete statement of what the student
    // wants saved. Each element is validated individually (the annotations sit
    // on the type argument), so one bad number is reported on its own rather
    // than rejecting the list with a vague message.
    // The cap here is on what was SENT and is only a sanity bound against an
    // absurd payload; the real limit of three is applied to the cleaned list
    // (blanks and duplicates removed) in Student.setContactNumbers.
    @Size(max = 10, message = "Too many contact numbers were sent")
    private List<@Size(max = 30, message = "Phone number must be 30 characters or fewer")
                 @Pattern(regexp = ValidationRules.PHONE_REGEX, message = ValidationRules.PHONE_MESSAGE)
                 String> phones;

    // profilePictureUrl WAS accepted here, and deliberately no longer is.
    //
    // It let a client register with any URL at all as their picture - an image
    // on another site that logs the IP address of every officer and
    // administrator whose screen displays it. Now that pictures are uploaded
    // (POST /api/students/{id}/avatar), the server sets this URL itself and
    // only ever to its own download endpoint. A client-supplied value would
    // also point somewhere other than the stored bytes, so the two could
    // disagree. Removing the field makes both problems unrepresentable.

    /**
     * Name rule across the two shapes: a full name, or at least a given name.
     *
     * @AssertTrue on a boolean method is how Bean Validation expresses a rule
     * that spans more than one field. It runs with every other constraint, so
     * a request missing both still gets a clean 400 with this message beside
     * the others, rather than failing later in the service.
     */
    @AssertTrue(message = "Full name is required")
    public boolean isNameProvided() {
        return (fullName != null && !fullName.isBlank())
                || (givenName != null && !givenName.isBlank());
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public List<String> getPhones() {
        return phones;
    }

    public void setPhones(List<String> phones) {
        this.phones = phones;
    }
}
