package com.helpdesk.profile.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.helpdesk.common.user.entity.AppUser;
import com.helpdesk.common.user.entity.Role;
import jakarta.persistence.Basic;
import org.hibernate.annotations.BatchSize;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.helpdesk.common.validation.ValidationRules;

import java.util.ArrayList;
import java.util.List;

/**
 * A university student, one of the three account types. Shared fields (email,
 * password, active...) are in AppUser's "users" table; this table holds the rest.
 * There is no role column or setRole(): the class decides the role, so a request
 * body can't make someone an admin.
 */
@Entity
@Table(name = "students")
@PrimaryKeyJoinColumn(name = "id")
public class Student extends AppUser {

    // university registration number, e.g. IT25101580
    @NotBlank(message = "Student ID is required")
    @Pattern(regexp = "^[A-Z]{2}\\d{8}$",
             message = "Student ID must be two letters followed by eight digits, e.g. IT25101580")
    @Column(name = "student_id", unique = true, nullable = false, length = 20)
    private String studentId;

    /**
     * Name is a composite attribute, stored as given name + surname; the full name is
     * derived in getFullName(). surname is nullable because some students have only one name.
     */
    @NotBlank(message = "Given name is required")
    @Size(max = 120, message = "Given name must be 120 characters or fewer")
    @Column(name = "given_name", nullable = false, length = 120)
    private String givenName;

    @Size(max = 120, message = "Surname must be 120 characters or fewer")
    @Column(name = "surname", length = 120)
    private String surname;

    /**
     * The student's faculty (e.g. "Faculty of Computing"), not a help desk Department.
     * No @NotBlank so a student can clear it.
     */
    @Size(max = 100, message = "Faculty must be 100 characters or fewer")
    @Column(name = "department", length = 100)
    private String department;

    /**
     * Multivalued attribute, so it gets its own table (student_contact_numbers).
     * @OrderColumn keeps the entered order, so index 0 is the main number ("position"
     * is an SQL keyword, hence list_index). @BatchSize avoids N+1 queries on the staff list.
     * At most three numbers.
     */
    @ElementCollection
    @BatchSize(size = 50)
    @CollectionTable(
            name = "student_contact_numbers",
            joinColumns = @JoinColumn(name = "student_id"),
            foreignKey = @ForeignKey(name = "fk_contact_number_student"))
    @OrderColumn(name = "list_index")
    @Column(name = "phone_number", nullable = false, length = 30)
    @Size(max = 3, message = "At most three contact numbers can be saved")
    private List<@NotBlank @Size(max = 30, message = "Phone number must be 30 characters or fewer") String>
            contactNumbers = new ArrayList<>();

    @Size(max = 500, message = "Profile picture URL must be 500 characters or fewer")
    @Column(name = "profile_picture_url", length = 500)
    private String profilePictureUrl;

    /**
     * Avatar bytes, stored in the database like Attachment, since files written into
     * resources at runtime don't survive packaging. Null means show initials.
     * MEDIUMBLOB is set explicitly: by default MySQL got TINYBLOB (255 bytes).
     * Note: LAZY is only a hint here; without bytecode enhancement the bytes still load.
     */
    @Basic(fetch = FetchType.LAZY)
    @JsonIgnore
    @Column(name = "profile_picture", columnDefinition = "MEDIUMBLOB")
    private byte[] profilePicture;

    // MIME type saved at upload, used as Content-Type when serving
    @Size(max = 100)
    @Column(name = "profile_picture_type", length = 100)
    private String profilePictureType;

    // Notification toggles. Officers have their own pair; admins have none.
    @Column(name = "email_notifications_enabled", nullable = false)
    private boolean emailNotificationsEnabled = true;

    @Column(name = "portal_notifications_enabled", nullable = false)
    private boolean portalNotificationsEnabled = true;

    public Student() {
        // for JPA
    }

    /** Fixed by the class, with no column or setter. */
    @Override
    public Role getRole() {
        return Role.STUDENT;
    }

    @Override
    public String getDisplayName() {
        return getFullName();
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName == null ? null : givenName.trim();
    }

    public String getSurname() {
        return surname;
    }

    /** Blank is stored as NULL. */
    public void setSurname(String surname) {
        this.surname = (surname == null || surname.isBlank()) ? null : surname.trim();
    }

    /** Derived from the two parts; there's no full_name column. */
    public String getFullName() {
        if (givenName == null) {
            return surname;
        }
        return surname == null ? givenName : givenName + " " + surname;
    }

    /**
     * Splits one full name for forms that send a single box: the last word is the
     * surname, e.g. "L. S. N. Perera" gives "L. S. N." + "Perera". One word means no surname.
     * Not right for every naming style, but the student can fix the parts later.
     */
    public void setFullName(String fullName) {
        if (fullName == null) {
            return;
        }
        String trimmed = fullName.trim().replaceAll("\\s+", " ");
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace < 0) {
            setGivenName(trimmed);
            setSurname(null);
        } else {
            setGivenName(trimmed.substring(0, lastSpace));
            setSurname(trimmed.substring(lastSpace + 1));
        }
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public List<String> getContactNumbers() {
        return contactNumbers;
    }

    /**
     * Replaces all numbers, after trimming and dropping blanks and duplicates.
     * Changes the list in place because Hibernate tracks this list instance.
     */
    public void setContactNumbers(List<String> numbers) {
        List<String> cleaned = new ArrayList<>();
        if (numbers != null) {
            for (String n : numbers) {
                if (n == null) continue;
                String t = n.trim();
                if (!t.isEmpty() && !cleaned.contains(t)) cleaned.add(t);
            }
        }
        // limit checked after cleaning, here so no code path can save a fourth (400)
        if (cleaned.size() > ValidationRules.MAX_CONTACT_NUMBERS) {
            throw new IllegalArgumentException("At most three contact numbers can be saved");
        }
        contactNumbers.clear();
        contactNumbers.addAll(cleaned);
    }

    /** First number or null; this is the "phone" field in the JSON. */
    public String getPrimaryContactNumber() {
        return contactNumbers.isEmpty() ? null : contactNumbers.get(0);
    }

    /** Sets only the first number and keeps the others. Blank removes it and the next moves up. */
    public void setPrimaryContactNumber(String number) {
        List<String> updated = new ArrayList<>(contactNumbers);
        String t = number == null ? "" : number.trim();
        if (updated.isEmpty()) {
            if (!t.isEmpty()) updated.add(t);
        } else if (t.isEmpty()) {
            updated.remove(0);
        } else {
            updated.set(0, t);
        }
        setContactNumbers(updated);
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }

    public boolean isEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }

    public void setEmailNotificationsEnabled(boolean emailNotificationsEnabled) {
        this.emailNotificationsEnabled = emailNotificationsEnabled;
    }

    public boolean isPortalNotificationsEnabled() {
        return portalNotificationsEnabled;
    }

    public void setPortalNotificationsEnabled(boolean portalNotificationsEnabled) {
        this.portalNotificationsEnabled = portalNotificationsEnabled;
    }

    public byte[] getProfilePicture() {
        return profilePicture;
    }

    public void setProfilePicture(byte[] profilePicture) {
        this.profilePicture = profilePicture;
    }

    public String getProfilePictureType() {
        return profilePictureType;
    }

    public void setProfilePictureType(String profilePictureType) {
        this.profilePictureType = profilePictureType;
    }
}
