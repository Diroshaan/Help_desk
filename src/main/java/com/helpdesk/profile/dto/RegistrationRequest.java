package com.helpdesk.profile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

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
 */
public class RegistrationRequest {

    @NotBlank(message = "Student ID is required")
    @Pattern(regexp = "^[A-Z]{2}\\d{8}$", message = "Student ID must be two letters followed by eight digits, e.g. IT25101580")
    private String studentId;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    // At least 8 characters, with an upper-case letter, a lower-case letter,
    // and a digit somewhere in it - see the class comment above for why this
    // lives here and not on Student.password.
    @NotBlank(message = "Password is required")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
            message = "Password must be at least 8 characters and include an upper-case letter, a lower-case letter, and a digit"
    )
    private String password;

    @NotBlank(message = "Department is required")
    private String department;

    // The registration form (frontend/src/pages/Register.jsx) sends this field
    // as "phone" in its JSON payload, not "contactNumber" - Jackson binds by
    // exact property name, so without @JsonProperty here, a request carrying
    // "phone" would silently fail to populate this field (Jackson ignores
    // unrecognised JSON properties by default rather than erroring), and the
    // student's phone number would just vanish with no validation failure to
    // reveal why. The Java-side name stays "contactNumber" to match Student's
    // and ProfileUpdateRequest's field of the same name.
    @JsonProperty("phone")
    private String contactNumber;

    private String profilePictureUrl;

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

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }
}
