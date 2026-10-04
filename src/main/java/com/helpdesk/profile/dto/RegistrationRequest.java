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
 * Request body for POST /api/students (registration).
 * The password rule is checked here because this is the only place the raw password
 * exists; the entity only ever holds the hash.
 * The @Size limits copy the entity's column lengths so a long value gets a clean 400
 * here instead of a Hibernate error on save. Keep them in step with Student and AppUser.
 */
public class RegistrationRequest {

    @NotBlank(message = "Student ID is required")
    @Pattern(regexp = "^[A-Z]{2}\\d{8}$", message = "Student ID must be two letters followed by eight digits, e.g. IT25101580")
    private String studentId;

    // Name can come as one fullName (split by the service) or as given name + surname.
    // isNameProvided() checks that at least one shape was sent.
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

    // Same rule as password change (ValidationRules). The max length is a BCrypt limit.
    @NotBlank(message = "Password is required")
    @Pattern(regexp = ValidationRules.PASSWORD_REGEX, message = ValidationRules.PASSWORD_MESSAGE)
    @Size(max = ValidationRules.PASSWORD_MAX_LENGTH, message = ValidationRules.PASSWORD_LENGTH_MESSAGE)
    private String password;

    @NotBlank(message = "Department is required")
    @Size(max = 100, message = "Faculty must be 100 characters or fewer")
    private String department;

    // The form sends "phone"; without @JsonProperty it would be silently dropped.
    // Treated as the first contact number.
    @JsonProperty("phone")
    @Size(max = 30, message = "Phone number must be 30 characters or fewer")
    @Pattern(regexp = ValidationRules.PHONE_REGEX, message = ValidationRules.PHONE_MESSAGE)
    private String contactNumber;

    // All contact numbers; wins over "phone" if both are sent. Each one is validated.
    // 10 is just a sanity cap; the real limit of three is in Student.setContactNumbers.
    @Size(max = 10, message = "Too many contact numbers were sent")
    private List<@Size(max = 30, message = "Phone number must be 30 characters or fewer")
                 @Pattern(regexp = ValidationRules.PHONE_REGEX, message = ValidationRules.PHONE_MESSAGE)
                 String> phones;

    // No profilePictureUrl: a client-chosen URL could be a tracking image that logs
    // staff IP addresses. The server sets it when a picture is uploaded.

    /** Cross-field check: needs a full name or at least a given name. */
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
