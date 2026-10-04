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
 * Publishing, editing and reading announcements. Each method is one transaction and
 * returns DTOs, so lazy fields are loaded before the transaction closes.
 */
@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final AppUserRepository appUserRepository;

    @Autowired
    public AnnouncementService(AnnouncementRepository announcementRepository,
                               AppUserRepository appUserRepository) {
        this.announcementRepository = announcementRepository;
        this.appUserRepository = appUserRepository;
    }

    // The publisher comes from the logged-in session, never the request body.
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

    // publishedBy and publishedAt stay as they were, so an edit doesn't change who posted it.
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
     * A hard delete is fine here: nothing else points at an announcement, unlike user
     * accounts. Its role rows are removed with it (@ElementCollection).
     */
    @Transactional
    public void delete(Long id) {
        if (!announcementRepository.existsById(id)) {
            throw notFound(id);
        }
        announcementRepository.deleteById(id);
    }

    // Includes expired notices so the admin can still edit or remove them.
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

    // The role comes from the caller's account, so a student can't ask for officer notices.
    @Transactional(readOnly = true)
    public List<AnnouncementResponse> findLiveFor(String callerEmail) {
        AppUser caller = appUserRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No account exists for the signed-in user."));

        return AnnouncementResponse.fromAll(
                announcementRepository.findLiveForRole(LocalDateTime.now(), caller.getRole()));
    }

    // SecurityConfig already checks the role; this also checks the account really is an
    // Administrator before we store it as the publisher.
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

    // null and empty both mean "visible to everyone"
    private Set<Role> rolesOrEmpty(Set<Role> roles) {
        return roles == null ? new HashSet<>() : new HashSet<>(roles);
    }

    // An expiry before the publish date would hide the notice straight away, so reject it.
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
