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
 * F1 - Student Profile & Preferences Management (Diroshaan S, IT25101580)
 *
 * A university student. One of the three account types in the system.
 *
 *
 * WHAT CHANGED, AND WHY IT MATTERS
 * --------------------------------
 * This class used to be a standalone @Entity holding everything about an
 * account: id, email, password, role, active, createdAt, plus the
 * student-specific fields below. It now extends AppUser, and the first six of
 * those have moved up to the shared "users" table.
 *
 * The reason is the requirement specification, 3.2:
 *
 *   "The system must store a single user record for every system actor ...
 *    The system must record each user as exactly one of Student, Help Desk
 *    Officer or System Administrator, with role-specific data held only against
 *    the relevant type."
 *
 * What was here before could not satisfy that. There was one table, "students",
 * holding a free-text role column - so an officer was a student row with the
 * word OFFICER in it, an administrator was the same, and there was nowhere at
 * all to put an officer's job title or staff number. Three features were
 * blocked on that: F4 assigns tickets to officers, F5 records an article's
 * authoring officer, F6 provisions officer and admin accounts.
 *
 * Practical consequences of the move, worth knowing before reading further:
 *
 *   - getEmail(), getPassword(), isActive(), getCreatedAt() and their setters
 *     are all still available. They are inherited from AppUser, not declared
 *     here, so every existing caller keeps working unchanged.
 *   - getRole() now returns the Role ENUM rather than a String, and it is
 *     computed from the class rather than read from a column. Nothing can set
 *     it, which is the point - see below.
 *   - The JSON the API produces is unchanged. StudentResponse decides that, and
 *     it lists the same fields as before.
 *
 *
 * WHY setRole() NO LONGER EXISTS
 * ------------------------------
 * StudentService.register() used to call student.setRole("STUDENT") explicitly,
 * to defend against a mass-assignment attack: role was bound straight from the
 * request body, so POST /api/students {"role": "ADMIN"} on a public,
 * unauthenticated endpoint would have granted the caller administrator
 * privileges.
 *
 * That defence is now unnecessary, because the attack is no longer expressible.
 * Role is not a field, not a column, and has no setter; it is decided by which
 * Java class was instantiated, and this endpoint only ever instantiates a
 * Student. There is nothing for a request body to overwrite.
 *
 * That is the better kind of fix. The old line worked, but it worked by
 * remembering to write it - and a future refactor that dropped it would
 * reintroduce a privilege-escalation hole silently. Making the bad state
 * impossible to represent needs nobody to remember anything.
 */
@Entity
@Table(name = "students")
@PrimaryKeyJoinColumn(name = "id")
public class Student extends AppUser {

    /**
     * The university-issued registration number - the "additional unique
     * identifier" the specification asks for, alongside the system-generated id
     * inherited from AppUser.
     *
     * @NotBlank alone would accept any non-empty string, so "x" or "not an id"
     * would register a real account. @Pattern pins it to the university's actual
     * format: two letters then eight digits, e.g. IT25101580.
     *
     * Safe to enforce on the entity rather than only on the request DTO, unlike
     * the password rule: studentId is never transformed after input, and
     * ProfileUpdateRequest does not expose it for editing, so a value that
     * passes this check at registration keeps passing it on every later save.
     * The password could not be treated this way - Hibernate re-validates every
     * field on every save, and the stored BCrypt hash would fail a complexity
     * rule written for the plain text.
     */
    @NotBlank(message = "Student ID is required")
    @Pattern(regexp = "^[A-Z]{2}\\d{8}$",
             message = "Student ID must be two letters followed by eight digits, e.g. IT25101580")
    @Column(name = "student_id", unique = true, nullable = false, length = 20)
    private String studentId;

    /**
     * The student's name, stored as the two components the specification asks
     * for: "the system must store the student's name as separate given name and
     * surname components" (Requirement Specification 3.2, User & Profile Data).
     *
     * WHY TWO COLUMNS AND NOT ONE full_name
     * -------------------------------------
     * A single full_name column is one atomic value in the database's eyes. The
     * database cannot sort by surname, cannot search "every Perera", and cannot
     * address a letter "Dear Kasun" without guessing where one part ends and the
     * other begins - and every guess is wrong for somebody. Storing the parts
     * separately is First Normal Form applied to a composite attribute: the EER
     * diagram draws Name as composite (given name, surname), and this is its
     * direct translation.
     *
     * The full name is now DERIVED - see getFullName() below - rather than stored
     * a third time. Storing it as well would be the same fact recorded twice,
     * in two places that could disagree after an edit. That is the same rule the
     * specification applies to resolution time and dashboard metrics.
     *
     * WHY surname IS NULLABLE
     * -----------------------
     * Not everybody has one. Mononyms are common in Sri Lanka and South India,
     * and a student called "Tharmithan" with no family name must still be able to
     * register. Forcing a surname would push people into typing a fake one or
     * repeating their given name, which is worse data than an honest NULL. The
     * given name is the one part everybody has, so that is the required one.
     *
     * 120 characters each, matching the old single column, so no existing name
     * can fail validation merely because it was split.
     */
    @NotBlank(message = "Given name is required")
    @Size(max = 120, message = "Given name must be 120 characters or fewer")
    @Column(name = "given_name", nullable = false, length = 120)
    private String givenName;

    @Size(max = 120, message = "Surname must be 120 characters or fewer")
    @Column(name = "surname", length = 120)
    private String surname;

    /**
     * The student's FACULTY - "Faculty of Computing", "Faculty of Engineering".
     *
     * NOT the same thing as common.reference.Department, which is a support desk
     * a ticket is routed to. The two concepts unfortunately share the English
     * word "department"; this field should be renamed to 'faculty' to remove the
     * ambiguity, which is a rename across the registration form, the profile
     * page and both DTOs and so is left as its own piece of work.
     *
     * No @NotBlank, deliberately: the faculty dropdown's first option is "Not
     * set" with value "", so a student clearing their faculty is a legitimate
     * save rather than an invalid one. This constraint has to be absent from the
     * ENTITY for that to work at all - Hibernate re-validates every field on
     * every save, so a @NotBlank here would reject the clearing save even with
     * the DTO-level check removed.
     */
    @Size(max = 100, message = "Faculty must be 100 characters or fewer")
    @Column(name = "department", length = 100)
    private String department;

    /**
     * The student's contact numbers - more than one, as the specification asks:
     * "the system must permit a student to record more than one contact number
     * against their profile" (Requirement Specification 3.2).
     *
     * WHY A SEPARATE TABLE
     * --------------------
     * A contact number is a MULTIVALUED attribute: one student, several values.
     * A relational column holds exactly one value, so the two usual shortcuts
     * are both wrong. phone1/phone2/phone3 columns fix a maximum in the schema
     * and leave NULLs everywhere; a comma-separated string makes the numbers
     * invisible to the database, so it can neither validate nor search them.
     * The textbook mapping of a multivalued attribute is its own table, keyed by
     * the owner's id - here student_contact_numbers(student_id, list_index,
     * phone_number), primary key (student_id, list_index) - which is exactly
     * what @ElementCollection generates.
     *
     * @ElementCollection rather than a full @Entity because a contact number has
     * no identity or life of its own: it exists only as part of one student, is
     * never shared, and is never looked up on its own. That is the definition of
     * a value type. Deleting the student row takes its numbers with it, and the
     * foreign key is named so it reads clearly in the generated ER diagram.
     *
     * @OrderColumn keeps the order the student entered them in, so "the first
     * number" is stable - the frontend, and any officer phoning the student, can
     * rely on index 0 being the one they put first. Without it a List is really
     * an unordered bag and the database may return the numbers in any order.
     * The column is "list_index" rather than "position" because POSITION is a
     * keyword in SQL, and a column named after a keyword works on one database
     * and fails on the next.
     *
     * LAZY (the default): the admin user listing loads every account through
     * AppUserRepository, and an EAGER collection here would fire one extra
     * query per student there. The profile endpoints read the numbers inside the
     * request, where the open session makes the lazy load safe.
     *
     * @BatchSize covers the one screen that DOES read every student's numbers -
     * the staff directory, GET /api/students. Without it, listing 50 students
     * costs 1 query for the students plus 50 for their numbers (the N+1
     * problem). With it, Hibernate loads the numbers for up to 50 students in a
     * single "WHERE student_id IN (...)" query when the first list is touched.
     *
     * The limit of three is a policy choice, not a technical one: enough for a
     * mobile, a home number and a guardian, few enough that the list stays a
     * list of the student's own numbers. It is enforced here AND on the request
     * DTOs, for the reason given on RegistrationRequest.
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
     * The avatar itself (F1 Update: "upload/update dynamic profile avatars").
     *
     * WHY THE BYTES LIVE IN THE DATABASE AND NOT ON DISK
     * --------------------------------------------------
     * The obvious alternative is writing the file under static/images/avatars/,
     * which SecurityConfig already makes public. It does not survive packaging:
     * src/main/resources is copied INTO the jar at build time, so a file written
     * there at runtime lands in target/classes on a developer machine and
     * nowhere at all once the application is a jar. The upload would appear to
     * work and the picture would vanish on the next build.
     *
     * Storing the bytes here follows what the rest of this project already does
     * - Attachment and Resolution both hold their files as byte[] - so backup
     * and restore cover everything at once and there are no orphaned files on a
     * disk nobody backs up. The cost is a larger database and a row that is
     * expensive to load.
     *
     * Nullable: most students never upload one, and the interface falls back to
     * their initials.
     *
     *
     * WHY columnDefinition RATHER THAN @Lob
     * -------------------------------------
     * This one cost an evening, so it is written down rather than quietly
     * fixed. @Lob is the annotation every tutorial reaches for and it looks
     * exactly right here. It is not.
     *
     * Hibernate 6 maps a byte[] field to VARBINARY, and an unbounded VARBINARY
     * on MySQL becomes TINYBLOB - which holds 255 BYTES. Not 255 kilobytes.
     * Every upload failed with "Data too long for column 'profile_picture'",
     * and because a column-length rejection arrives as a
     * DataIntegrityViolationException, GlobalExceptionHandler reported it to
     * the student as "That value is already in use by another account". A
     * photograph, described as a duplicate email address.
     *
     * The failure was also invisible from the outside: ddl-auto=update had
     * already created the column as TINYBLOB at startup, so the migration's
     * ALTER TABLE ... ADD COLUMN failed as a duplicate, and DBeaver's cached
     * metadata panel kept showing the type the migration INTENDED rather than
     * the one the server actually had. Only information_schema told the truth.
     *
     * columnDefinition states the type outright, so Hibernate and MySQL cannot
     * disagree about it and a fresh database gets the right column first time.
     * The trade-off is honest: MEDIUMBLOB is MySQL syntax rather than portable
     * JPA, so a move to PostgreSQL would need this changed. H2 accepts it too,
     * which covers both databases this project actually uses - and a column
     * 65,000 times smaller than intended is by far the worse problem to keep.
     *
     *
     * WHAT @Basic(fetch = LAZY) DOES HERE, HONESTLY
     * ----------------------------------------------
     * Less than it looks like, and it is worth being precise rather than
     * claiming a benefit this project does not actually get.
     *
     * For an ASSOCIATION (@ManyToOne, @OneToOne) lazy loading works out of the
     * box: Hibernate hands back a proxy and fetches the real row on first use.
     * For a BASIC attribute like this byte[] it does not. Lazy basic fetching
     * needs the entity class to be rewritten at build time - bytecode
     * enhancement, via hibernate-enhance-maven-plugin - so that reading the
     * field can be intercepted. This project does not run that plugin, so the
     * JPA specification's "this is a hint" applies and Hibernate ignores it:
     * the bytes ARE loaded on every read of a Student.
     *
     * The annotation is kept because it states the intent, costs nothing, and
     * starts working the day the plugin is added. It is NOT load-bearing today,
     * and anywhere this class is described it should not be claimed as an
     * optimisation that is already in effect.
     *
     * The real fix, if the cost ever shows up in practice, is not the plugin
     * but a separate table - a StudentAvatar entity joined by a lazy @OneToOne -
     * because lazy loading of an ASSOCIATION needs no enhancement at all. That
     * is a schema change, so it is recorded here as the known next step rather
     * than done in the same commit that got the column type right.
     */
    @Basic(fetch = FetchType.LAZY)
    @JsonIgnore
    @Column(name = "profile_picture", columnDefinition = "MEDIUMBLOB")
    private byte[] profilePicture;

    /**
     * The image's MIME type, so the download endpoint can set Content-Type
     * correctly rather than guessing from the bytes.
     *
     * Stored rather than derived because the browser needs it on the way out,
     * and re-sniffing the bytes on every request to answer a question already
     * answered at upload would be work for nothing.
     */
    @Size(max = 100)
    @Column(name = "profile_picture_type", length = 100)
    private String profilePictureType;

    /**
     * Notification channel preferences (F1: toggle Email / Portal alerts).
     *
     * On Student rather than AppUser on purpose, even now that officers have
     * preferences too (US-04, see Officer). The two mean different things: a
     * student is told what happened to their own tickets, an officer is told
     * that new tickets have landed in their queue. Same two channels, different
     * events - so each subtype owns its own pair rather than the supertype
     * pretending they are one setting. Administrators have none, because no
     * requirement asks for any.
     */
    @Column(name = "email_notifications_enabled", nullable = false)
    private boolean emailNotificationsEnabled = true;

    @Column(name = "portal_notifications_enabled", nullable = false)
    private boolean portalNotificationsEnabled = true;

    // --- Constructors ---

    public Student() {
        // Required no-argument constructor for JPA
    }

    // --- Role ---

    /**
     * Always STUDENT, because this class is the student type.
     *
     * Read by StudentUserDetailsService to build the Spring Security authority
     * "ROLE_STUDENT", and by StudentResponse to report the role to the frontend.
     * There is no corresponding setter and no column behind it - see the class
     * comment above for why that is the whole point.
     */
    @Override
    public Role getRole() {
        return Role.STUDENT;
    }

    /**
     * A student's display name is their full name, derived from its parts.
     *
     * See AppUser.getDisplayName() for why every account type answers this
     * question itself rather than sharing one column.
     */
    @Override
    public String getDisplayName() {
        return getFullName();
    }

    // --- Getters and setters ---
    //
    // Note there are none for id, email, password, active or createdAt: those
    // are inherited from AppUser. Redeclaring them here would shadow the
    // parent's and leave two fields where the code expects one.

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

    /** Blank is stored as NULL: "no surname" has one representation, not two. */
    public void setSurname(String surname) {
        this.surname = (surname == null || surname.isBlank()) ? null : surname.trim();
    }

    /**
     * The full name, DERIVED from the two stored parts - there is no full_name
     * column any more.
     *
     * Every existing caller keeps working unchanged: the admin user listing,
     * the sidebar, the activity labels and every JSON response still receive a
     * "fullName". Only the storage changed, which is the point of hiding it
     * behind a method. JPA ignores this getter because the entity is mapped by
     * FIELD (the annotations sit on the fields), so a getter with no field
     * behind it creates no column.
     */
    public String getFullName() {
        if (givenName == null) {
            return surname;
        }
        return surname == null ? givenName : givenName + " " + surname;
    }

    /**
     * Accept a single full name and split it into the two parts.
     *
     * This exists for backward compatibility: the registration and profile
     * pages send one "fullName" box, and they keep working without a frontend
     * change. A caller that knows the parts should use setGivenName() and
     * setSurname() instead.
     *
     * The rule is "the LAST word is the surname, everything before it is the
     * given name". It is a heuristic and it is written down as one, because no
     * rule is right for every naming convention:
     *
     *   "Diro Ruban"                -> given "Diro",            surname "Ruban"
     *   "Shaleel Dakshina Amarasinghe" -> given "Shaleel Dakshina", surname "Amarasinghe"
     *   "L. S. N. Perera"           -> given "L. S. N.",        surname "Perera"
     *   "Tharmithan"                -> given "Tharmithan",      surname NULL
     *
     * Last-word-is-surname is right for the common Sri Lankan and Western
     * forms, including initials-first. It is wrong for, say, a Tamil name
     * written patronymic-first; that student can correct it once separate
     * boxes exist on the profile page. Getting some names wrong and letting the
     * owner fix them is better than refusing to register them.
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

    /** All saved numbers, in the order the student entered them. */
    public List<String> getContactNumbers() {
        return contactNumbers;
    }

    /**
     * Replace every saved number with the given list.
     *
     * Cleans the input on the way in - trims, drops blanks, removes exact
     * duplicates while keeping first-seen order - so "the same number twice"
     * or an empty extra box on a form never becomes a row. The collection is
     * modified IN PLACE rather than reassigned: Hibernate tracks this List
     * instance, and swapping in a new one makes it delete and re-insert every
     * row instead of applying the difference.
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
        // The limit is checked on the CLEANED list, not on what was sent. Four
        // boxes where one is blank and one repeats another is two numbers, and
        // refusing it as "more than three" would blame the student for our own
        // tidying. Enforced here, in the entity, so no code path - registration,
        // profile update, or anything written later - can store a fourth.
        // IllegalArgumentException becomes a 400 with this message.
        if (cleaned.size() > ValidationRules.MAX_CONTACT_NUMBERS) {
            throw new IllegalArgumentException("At most three contact numbers can be saved");
        }
        contactNumbers.clear();
        contactNumbers.addAll(cleaned);
    }

    /**
     * The first number, or null - what the single "phone" field in the JSON
     * has always meant. Kept so every existing reader of "phone" still works.
     */
    public String getPrimaryContactNumber() {
        return contactNumbers.isEmpty() ? null : contactNumbers.get(0);
    }

    /**
     * Set the FIRST number and keep any others.
     *
     * This is what the current profile page's single phone box means: editing
     * it must not silently delete a second number the student saved some other
     * way. Blank clears only the first number, and the next one moves up.
     */
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
