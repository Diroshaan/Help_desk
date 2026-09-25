package com.helpdesk.profile.service;

import com.helpdesk.auth.SessionRevoker;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.common.exception.ResourceNotFoundException;
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
 * Business logic for Student profiles (US-01, US-02, US-03, US-06).
 * Controllers should stay thin and call methods here - this is where
 * validation rules, uniqueness checks, and business decisions live.
 *
 * A note on @Transactional, which appears on every method below.
 *
 * Without it, Spring Data still opens a transaction around each individual
 * repository call, but each call gets its OWN transaction. That is fine for a
 * method that makes a single call, and wrong for every method here that makes
 * two or more: register() queries then saves, updateProfile() loads then saves,
 * deactivate() loads then saves. Between those two statements the connection is
 * released and another request can interleave. Annotating the SERVICE method
 * puts all of its repository calls inside one transaction, so the whole
 * business operation either happens or does not.
 *
 * It is annotated here rather than on the controller or the repository on
 * purpose: the service method is the unit of work - the thing that has a
 * meaningful "all or nothing" boundary. A repository call is too small (it is
 * one statement) and a controller method is too big (it also does HTTP work
 * that has no business being inside a database transaction).
 *
 * readOnly = true on the finders is not decoration: it tells Hibernate to skip
 * dirty-checking the entities it loads, and tells the driver the transaction
 * will not write. It is also the honest description of what those methods do.
 *
 * That boundary is now carrying a second job. The activity-log entries written
 * below join the same transaction, so an entry can never survive an operation
 * that rolled back - the log cannot claim a profile was updated when it was
 * not. See ActivityLogService for the full reasoning.
 */
@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;

    /**
     * Ends the account's live sessions on deactivation - the "revoking session
     * tokens" half of F1's Account Deletion sub-function, which was specified
     * and not built until now.
     */
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

    // Create (US-03: register a new student account)
    //
    // Takes a RegistrationRequest, not the Student entity itself (which this
    // method originally did) - see the comment on RegistrationRequest for the
    // main reason (the password complexity rule needs the raw password, which
    // only exists here, before hashing).
    @Transactional
    public Student register(RegistrationRequest request) {
        // These two checks LOOK the row up rather than asking existsBy...().
        //
        // "Does a row with this Student ID exist?" is the wrong question, because
        // it returns true in two situations that need different answers: an
        // account somebody is using, and an account that was soft-deleted under
        // US-02. Only the full row can tell them apart, via isActive().
        // See rejectIfAlreadyTaken() below for what each case should say.
        rejectIfAlreadyTaken(studentRepository.findByStudentId(request.getStudentId()), "This Student ID");
        rejectIfAlreadyTaken(studentRepository.findByEmail(request.getEmail()), "This email address");

        Student student = new Student();
        student.setStudentId(request.getStudentId());
        applyName(student, request.getFullName(), request.getGivenName(), request.getSurname());
        student.setEmail(request.getEmail());
        student.setDepartment(request.getDepartment());

        // "phones" (the full list) wins over "phone" (one number) when both are
        // sent - see RegistrationRequest. The picture URL is no longer taken from
        // the request at all; it is set only by an upload.
        if (request.getPhones() != null) {
            student.setContactNumbers(request.getPhones());
        } else {
            student.setPrimaryContactNumber(request.getContactNumber());
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

        // There used to be an explicit role-forcing line here, and its removal
        // is worth understanding rather than glossing over.
        //
        // The line existed to close a mass-assignment hole. Role was a plain
        // String field on the Student entity with no write protection, so a
        // request like
        //   POST /api/students { ..., "role": "ADMIN" }
        // to this public, unauthenticated endpoint would have let anyone grant
        // themselves administrator privileges. Overwriting the field here, after
        // validation and before save, discarded whatever the client sent.
        //
        // It is gone because the attack is no longer expressible. Under the user
        // supertype, role is not a field, not a column, and has no setter: it is
        // decided by which class was instantiated, and this method only ever
        // instantiates a Student (see Student.getRole()). There is nothing left
        // for a request body to overwrite.
        //
        // That is a stronger fix than the line it replaces. The old defence
        // worked by being remembered - delete it during a refactor and the hole
        // reopens silently, with nothing failing to say so. Making the bad state
        // unrepresentable needs nobody to remember anything.
        //
        // Creating Officer and Administrator accounts still needs its own
        // access-controlled path, which is F6's to build.

        // Never store the plain-text password - hash it before saving.
        student.setPassword(passwordEncoder.encode(request.getPassword()));

        // The checks at the top of this method reduce how often two people can
        // claim the same Student ID; they do not prevent it. Two requests can both
        // run their lookup before either reaches this line, and both find nothing.
        // What actually prevents the duplicate row is @Column(unique = true) on
        // Student.studentId and Student.email - a guarantee the database enforces
        // and no amount of application code can be raced past. When it fires,
        // GlobalExceptionHandler turns the resulting DataIntegrityViolationException
        // into the same 409 the pre-check would have produced, so the loser of the
        // race gets a sensible answer instead of a 500.
        Student saved = studentRepository.save(student);

        // Recorded AFTER save because that is when the id exists - the log entry
        // has nothing to attach itself to before the insert.
        activityLogService.record(saved.getId(), ActivityType.ACCOUNT_CREATED,
                "Account created.");

        return saved;
    }

    /**
     * Registration is refused when an identifier is already in use - and the
     * message has to depend on WHY, because the two cases need different
     * actions from the student.
     *
     * ACTIVE account: a plain duplicate. "Already registered, please log in"
     * is accurate and tells them what to do.
     *
     * DEACTIVATED account (the US-02 soft delete): the row is still there, it
     * still holds the old password hash, and every ticket that student ever
     * raised still points at it. The tempting fix is to reactivate the row and
     * set the new password - and that would be an account-takeover hole, not a
     * feature. POST /api/students is public and unauthenticated by design (you
     * cannot log in before your account exists), so anyone who knows a
     * deactivated student's ID and email could claim the account and inherit
     * its entire ticket history. Restoring an account is a privileged
     * operation; it belongs to the admin story (US-05), not to self-service
     * registration. So this refuses, and says who can undo it.
     *
     * What this replaced: existsByStudentId() / existsByEmail(), which return
     * true for both cases and produced one message for both - "Student ID
     * already registered". True, but it reads as "somebody else has your ID"
     * and leaves a student who deleted their own account with nothing to do
     * and no idea why their own university ID is refused.
     */
    private void rejectIfAlreadyTaken(Optional<Student> existing, String label) {
        existing.ifPresent(student -> {
            if (student.isActive()) {
                throw new DuplicateResourceException(
                        label + " is already registered. Please log in instead.");
            }
            throw new DuplicateResourceException(
                    label + " belongs to an account that has been deactivated. "
                            + "Contact the help desk administrator to have it restored.");
        });
    }

    // Read
    @Transactional(readOnly = true)
    public List<Student> findAll() {
        return studentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Student> findById(Long id) {
        return studentRepository.findById(id);
    }

    // Used by GET /api/students/me - looks a student up by the email they
    // logged in with (Authentication.getName()), rather than by a numeric id
    // the frontend would otherwise have no way to know.
    @Transactional(readOnly = true)
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
    //
    // The null checks now do a second job. Each one also asks whether the value
    // is actually DIFFERENT from what is stored, and collects the names of the
    // fields that really changed. That is what lets the activity log say
    // "Profile updated: full name, faculty" instead of a bare "Profile updated"
    // - and it means pressing Save without editing anything records nothing,
    // rather than filling the student's history with entries about no change.
    @Transactional
    public Student updateProfile(Long id, ProfileUpdateRequest updatedDetails) {
        // ResourceNotFoundException, not IllegalArgumentException.
        //
        // Both are unchecked and both would compile, but GlobalExceptionHandler
        // maps them to different statuses - 404 and 400 - and only one of those
        // is true here. An id that refers to no student is not a malformed
        // request; the request is perfectly well formed and names something that
        // does not exist. Using the same exception type the rest of the codebase
        // uses for the same situation (see BookmarkFolderService) also means the
        // API answers consistently no matter which package handled the call,
        // which is the part a client can actually rely on.
        Student existing = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        // Field labels as a student would recognise them, not as Java names -
        // this text ends up on their profile page.
        List<String> changedDetails = new ArrayList<>();
        boolean preferencesChanged = false;

        // The name, in whichever shape the caller sent. Compared as the DERIVED
        // full name before and after, so "Diro Ruban" re-sent as
        // given "Diro" + surname "Ruban" is correctly recorded as no change.
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
        // Contact numbers. The list is copied BEFORE the change because the
        // entity edits its collection in place - comparing afterwards against
        // the live list would always find them equal.
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
        // profilePictureUrl is no longer editable here - see ProfileUpdateRequest.
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

        // Two separate entries rather than one, because they are two separate
        // actions to the student: "Save changes" and "Save preferences" are
        // different buttons on different sections of the page. A request that
        // genuinely changed both records both - which is honest, and only
        // happens if a client sends both at once.
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

    // Delete (US-02: self-service account deletion - soft delete, not a hard DB delete)
    @Transactional
    public void deactivate(Long id) {
        // Same reasoning as updateProfile above: a missing id is a 404, not a 400.
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        student.setActive(false);
        studentRepository.save(student);

        // REVOKE THE SESSION, not just the ability to start a new one.
        //
        // .disabled(!isActive()) in StudentUserDetailsService only runs while
        // authenticating, so on its own it stops a deactivated student logging
        // IN and does nothing about the one who already did. Their session
        // stays valid until it happens to expire.
        //
        // For a student closing their own account the browser signs itself out
        // anyway, so this changes little. It matters for the case F1 does not
        // control: an administrator suspending someone under US-05, where the
        // suspended user is at their own computer and nothing tells their
        // browser anything. Suspension a suspended person can ignore is not
        // suspension.
        //
        // Deliberately after the save. If the save fails the account is still
        // active, and throwing someone out of a session they are entitled to
        // would be a bug of our own making.
        sessionRevoker.revokeAllSessionsFor(student.getEmail());

        // Deliberately kept, not deleted along with the account. The student's
        // row survives deactivation (that is what "soft delete" means here), so
        // their history survives with it - which is the whole point of an audit
        // trail, and what makes it possible for an administrator to see what
        // happened if the account is ever restored under US-05.
        activityLogService.record(student.getId(), ActivityType.ACCOUNT_DEACTIVATED,
                "Account closed by the account holder.");
    }

    /**
     * Set a student's name from whichever shape the request used.
     *
     * The separate parts win when present, because they are exact; the single
     * fullName is split by the entity's heuristic only when the parts were not
     * given (see Student.setFullName for the rule and its known limits).
     *
     * On an update, sending ONLY a surname changes only the surname - the
     * given name is left as stored. That is the same partial-update rule every
     * other field on the profile follows: absent means "leave it alone".
     */
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

    // ------------------------------------------------------------------
    // Avatar (F1 Update: "upload/update dynamic profile avatars")
    // ------------------------------------------------------------------

    /**
     * Accepted image types. A allow-list, not a block-list: naming what IS
     * permitted means a type nobody considered is rejected by default, where a
     * block-list lets anything unanticipated straight through.
     */
    private static final Set<String> ALLOWED_AVATAR_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    /** 2MB. Comfortably more than a profile photograph needs. */
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;

    /**
     * Store a new profile picture for this student.
     *
     * WHY THE CONTENT TYPE IS CHECKED AGAINST THE BYTES, NOT THE FILENAME
     * -------------------------------------------------------------------
     * A file extension is whatever the uploader typed. Renaming shell.jsp to
     * avatar.png changes nothing about what the file contains, so trusting the
     * name is trusting an attacker. The declared MIME type is better but still
     * client-supplied, so the first bytes are checked too: every format on the
     * list has a fixed signature at the start of the file, and those cannot be
     * renamed away.
     *
     * Belt and braces is warranted here specifically because this endpoint takes
     * a file from an unauthenticated-in-spirit source (any logged-in student)
     * and stores it to be served back to browsers later.
     *
     * The URL written into profilePictureUrl points at this application's own
     * download endpoint, so the rest of the system - the profile page, the top
     * bar, the admin listing - keeps reading one String field and needs no
     * change at all.
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
            // The upload failed in transit. Not the student's mistake, and not
            // something a clearer message would help them fix.
            throw new IllegalStateException("We could not read that file. Please try again.", e);
        }

        // WHAT THE BYTES ACTUALLY ARE, not what the upload claimed.
        //
        // detectImageType returns the type read from the file's own signature,
        // or null if it matches none of the three we accept. Taking the DETECTED
        // type as the value to store - rather than the declared one - closes a
        // gap the earlier version left open: a JPEG uploaded with a declared
        // type of image/png passed both checks (it is a real image, and png is
        // on the allow-list) and was then stored and served as image/png. The
        // browser would sniff and usually render it anyway, so the bug was
        // invisible until something trusted the Content-Type.
        //
        // The declared type is still checked first, above. That rejection is
        // cheaper and gives a clearer message for the ordinary case of someone
        // choosing a PDF.
        String detectedType = detectImageType(bytes);
        if (detectedType == null) {
            throw new IllegalArgumentException(
                    "That file is not a valid image, whatever its name says.");
        }

        student.setProfilePicture(bytes);
        student.setProfilePictureType(detectedType);

        // Relative, not absolute: an absolute URL would bake in the host and
        // break the moment this runs anywhere other than localhost:8080.
        //
        // THE ?v= IS NOT DECORATION - without it, replacing a picture appears to
        // do nothing. The path is identical for every upload by the same
        // student, and the download endpoint sets Cache-Control: max-age=300, so
        // after an update the browser has a perfectly valid cached copy of the
        // OLD image and sees no reason to ask again for up to five minutes. The
        // upload succeeds, the database is correct, and the page still shows the
        // previous photograph.
        //
        // Changing the query string changes the cache key, so the browser
        // fetches the new image immediately - while still caching it for the
        // several places one page shows the same avatar. This is the standard
        // fix and it is why it is a timestamp rather than a random number: two
        // uploads in the same millisecond by the same student would collide, and
        // that is not a case worth defending against.
        student.setProfilePictureUrl(
                "/api/students/" + student.getId() + "/avatar?v=" + System.currentTimeMillis());

        Student saved = studentRepository.save(student);
        activityLogService.record(saved.getId(), ActivityType.PROFILE_UPDATED,
                "Profile picture updated.");
        return saved;
    }

    /**
     * Identify the image from its file signature - the "magic bytes" every
     * format begins with. Returns the MIME type, or null if these bytes are not
     * one of the three formats this system accepts.
     *
     *   JPEG  FF D8 FF
     *   PNG   89 50 4E 47        ("\x89PNG")
     *   WebP  "RIFF" ???? "WEBP"  (bytes 4-7 are the file length, so they are
     *                              skipped rather than matched)
     *
     * This returns the type rather than a boolean on purpose. The caller needs
     * to STORE a content type, and the only trustworthy source for it is the
     * file itself: the filename is whatever the uploader typed, and the declared
     * MIME type is whatever their browser - or their script - chose to send.
     * Both are client-supplied. The signature is the one thing in the request
     * that cannot be renamed away, so it is the one thing worth recording.
     *
     * bytes.length < 12 is rejected outright. No valid file of any of these
     * formats is that small, and it also guarantees every index read below is in
     * bounds, so the checks can be written plainly without a length test each.
     */
    private String detectImageType(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "image/png";
        }
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    /** The stored avatar, for the download endpoint. */
    @Transactional(readOnly = true)
    public Student getWithAvatar(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
    }
}
