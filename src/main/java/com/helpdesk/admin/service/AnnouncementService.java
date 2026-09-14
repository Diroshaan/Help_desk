package com.helpdesk.admin.service;

import com.helpdesk.admin.dto.AnnouncementRequest;
import com.helpdesk.admin.dto.AnnouncementResponse;
import com.helpdesk.admin.entity.Announcement;
import com.helpdesk.admin.repository.AnnouncementRepository;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.user.entity.Administrator;
import com.helpdesk.common.user.entity.AppUser;
import com.helpdesk.common.user.entity.Role;
import com.helpdesk.common.user.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * F6 - System Analytics, Provisioning & Announcements
 *
 * Publishing, editing and reading system-wide notices (WBHD-34, WBHD-36).
 *
 * @Transactional is on the service methods rather than on the controller or the
 * repository, for the reason StudentService gives: the service method is the
 * unit of work. publish() loads an administrator and then saves an
 * announcement; without one transaction around both, the connection is released
 * in between and another request can interleave.
 *
 * Every method returns DTOs, never entities. Announcement.publishedBy and
 * visibleToRoles are both LAZY, and a controller converting entities after the
 * transaction closed would throw LazyInitializationException. Converting inside
 * the service makes that class of bug impossible rather than merely unlikely -
 * the persistence context never escapes this layer. Same rule, same reasoning
 * as ReferenceDataService.
 */
@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final AppUserRepository appUserRepository;

    /**
     * Constructor injection rather than @Autowired fields, matching
     * StudentService and ReferenceDataService: an object that exists is an
     * object that is fully wired, the fields can be final, and a plain unit test
     * can supply test doubles without reflection.
     */
    @Autowired
    public AnnouncementService(AnnouncementRepository announcementRepository,
                               AppUserRepository appUserRepository) {
        this.announcementRepository = announcementRepository;
        this.appUserRepository = appUserRepository;
    }

    // ------------------------------------------------------------------
    // Write side - administrators only (enforced by SecurityConfig's
    // /api/admin/** rule, so nothing here re-checks the role)
    // ------------------------------------------------------------------

    /**
     * Requirement 3: record the administrator who published each announcement.
     *
     * The publisher comes from the SECURITY CONTEXT, never from the request
     * body - AnnouncementRequest deliberately has no publishedBy field. That is
     * what makes the audit trail worth having: an administrator cannot attribute
     * their own notice to a colleague, because the only source of that value is
     * the session they authenticated with.
     */
    @Transactional
    public AnnouncementResponse publish(AnnouncementRequest request, String publisherEmail) {
        Administrator publisher = requireAdministrator(publisherEmail);

        Announcement announcement = new Announcement(
                request.title(),
                request.body(),
                request.expiresAt(),
                publisher,
                rolesOrEmpty(request.visibleToRoles())
        );

        validateExpiry(announcement.getPublishedAt(), announcement.getExpiresAt());

        return AnnouncementResponse.from(announcementRepository.save(announcement));
    }

    /**
     * Edit an existing notice.
     *
     * publishedBy and publishedAt are deliberately NOT updated. The record is of
     * who published the announcement and when, and rewriting either on every
     * edit would mean the second administrator to touch a notice quietly becomes
     * its author - which is the audit trail requirement 3 asks for, erased.
     * (If "last edited by" is ever wanted it is a second pair of columns, not a
     * reinterpretation of these.)
     */
    @Transactional
    public AnnouncementResponse update(Long id, AnnouncementRequest request) {
        Announcement announcement = announcementRepository.findByIdWithPublisher(id)
                .orElseThrow(() -> notFound(id));

        validateExpiry(announcement.getPublishedAt(), request.expiresAt());

        announcement.setTitle(request.title());
        announcement.setBody(request.body());
        announcement.setExpiresAt(request.expiresAt());
        announcement.setVisibleToRoles(rolesOrEmpty(request.visibleToRoles()));

        return AnnouncementResponse.from(announcementRepository.save(announcement));
    }

    /**
     * Remove a notice entirely.
     *
     * A HARD delete, and it is the one place in this feature where that is
     * right. Accounts are soft-deleted because tickets, bookmarks and log rows
     * point at them and removing the row would destroy the history of every
     * ticket that person handled. Nothing points at an announcement - it is a
     * banner, not a record of anything that happened - so there is no history to
     * protect and an 'active' flag would just be a second way to say "expired",
     * which expiresAt already says. Setting expiresAt to the past is the
     * hide-without-deleting option, and it is already available.
     *
     * The rows in announcement_visible_roles go with it automatically: that is
     * what @ElementCollection means, and one of the reasons those roles are a
     * value collection rather than an entity - an entity would leave orphans.
     */
    @Transactional
    public void delete(Long id) {
        if (!announcementRepository.existsById(id)) {
            throw notFound(id);
        }
        announcementRepository.deleteById(id);
    }

    // ------------------------------------------------------------------
    // Read side
    // ------------------------------------------------------------------

    /**
     * Every announcement including expired ones - the administrator's
     * management list. Expired notices are shown, flagged by
     * AnnouncementResponse.expired, because an administrator managing notices
     * needs to see the ones that have lapsed in order to edit or remove them.
     */
    @Transactional(readOnly = true)
    public List<AnnouncementResponse> findAllForAdmin() {
        return AnnouncementResponse.fromAll(announcementRepository.findAllWithPublisher());
    }

    @Transactional(readOnly = true)
    public AnnouncementResponse findById(Long id) {
        return announcementRepository.findByIdWithPublisher(id)
                .map(AnnouncementResponse::from)
                .orElseThrow(() -> notFound(id));
    }

    /**
     * Requirement 4 in action: the live notices THIS caller is permitted to see.
     *
     * The caller's role is resolved from their account rather than taken from
     * the request, so a student cannot ask for the officers' notices by sending
     * a different role. The filtering happens in the query (see
     * AnnouncementRepository.findLiveForRole) rather than by loading everything
     * and filtering in Java - the database should return the rows that are
     * wanted, not every row for the application to sift through.
     */
    @Transactional(readOnly = true)
    public List<AnnouncementResponse> findLiveFor(String callerEmail) {
        AppUser caller = appUserRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No account exists for the signed-in user."));

        return AnnouncementResponse.fromAll(
                announcementRepository.findLiveForRole(LocalDateTime.now(), caller.getRole()));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * The signed-in administrator, as an Administrator rather than an AppUser.
     *
     * findByEmail is polymorphic across the whole JOINED hierarchy, so it
     * returns whatever type of account holds that address. The instanceof check
     * is what turns "some authenticated user" into "an administrator" - and it
     * is not redundant with SecurityConfig's hasRole("ADMIN"). That rule guards
     * the URL; this guards the TYPE the code is about to store in a column
     * declared Administrator. If the two ever disagree - a rule edited, a method
     * called from somewhere new - this fails with a clear message instead of a
     * ClassCastException from inside Hibernate.
     */
    private Administrator requireAdministrator(String email) {
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No account exists for the signed-in user."));

        if (!(user instanceof Administrator administrator)) {
            throw new IllegalArgumentException(
                    "Only an administrator can publish announcements.");
        }
        return administrator;
    }

    /** Null and empty mean the same thing here - see Announcement.isVisibleTo. */
    private Set<Role> rolesOrEmpty(Set<Role> roles) {
        return roles == null ? new HashSet<>() : new HashSet<>(roles);
    }

    /**
     * An expiry date before the publication date is not a strange edge case, it
     * is a typo - and the result is a notice that is expired the instant it is
     * created, appears nowhere, and gives the administrator no clue why. Caught
     * here rather than left to the database, which has no opinion about the
     * relationship between two nullable timestamps.
     *
     * This is a cross-field rule, which is why it is not a bean-validation
     * annotation on AnnouncementRequest: @Size and @NotBlank check one field
     * against a constant, and a comparison between two fields needs either a
     * custom class-level constraint or a line of service code. One line is the
     * proportionate answer for one rule.
     */
    private void validateExpiry(LocalDateTime publishedAt, LocalDateTime expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(publishedAt)) {
            throw new IllegalArgumentException(
                    "The expiry date must be after the publication date.");
        }
    }

    private ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("No announcement exists with id " + id + ".");
    }
}
