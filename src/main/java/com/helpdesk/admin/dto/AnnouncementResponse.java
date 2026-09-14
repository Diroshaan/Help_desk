package com.helpdesk.admin.dto;

import com.helpdesk.admin.entity.Announcement;
import com.helpdesk.common.user.entity.Role;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * F6 - System Analytics, Provisioning & Announcements
 *
 * What an announcement looks like when it leaves the API.
 *
 * The entity is never serialised directly. Here there is no password to
 * protect, so the argument is the one DepartmentResponse makes: if the JSON is
 * produced from the entity, every field anybody adds to Announcement silently
 * becomes part of the public contract and the frontend starts depending on it.
 * A DTO makes the wire format something decided on purpose.
 *
 * publishedBy is flattened to an id and a display name rather than nested as a
 * whole Administrator. Serialising the administrator would mean serialising an
 * AppUser subclass - and AppUser carries the BCrypt hash. Flattening to the two
 * fields the UI actually shows means the hash has no path to the wire at all,
 * which is structural protection rather than an annotation somebody can delete.
 * Same rule as profile/dto/StudentResponse.java: no password field anywhere.
 */
public record AnnouncementResponse(
        Long id,
        String title,
        String body,
        LocalDateTime publishedAt,
        LocalDateTime expiresAt,
        Long publishedById,
        String publishedByName,
        Set<String> visibleToRoles,
        boolean expired
) {

    /**
     * The one place that knows how to turn an Announcement into its response
     * form, so a change to the wire format is a change in exactly one file.
     *
     * MUST BE CALLED INSIDE THE SERVICE TRANSACTION. It reads publishedBy,
     * which is LAZY; calling this from a controller after the transaction has
     * closed throws LazyInitializationException. That is why every method in
     * AnnouncementService returns DTOs rather than entities - the same rule
     * ReferenceDataService follows, and the reason it gives.
     *
     * 'expired' is computed here rather than stored, so the client never has to
     * compare timestamps itself and two clients can never disagree about
     * whether a notice is still live.
     *
     * The roles are converted to Strings in a TreeSet so the JSON array is
     * alphabetical and stable. A HashSet of enums iterates in an order that
     * depends on hash codes, which makes the response body change shape between
     * runs for no reason and turns any test comparing whole payloads into a
     * flaky one.
     */
    public static AnnouncementResponse from(Announcement announcement) {
        Set<String> roles = new TreeSet<>();
        for (Role role : announcement.getVisibleToRoles()) {
            roles.add(role.name());
        }
        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getPublishedAt(),
                announcement.getExpiresAt(),
                announcement.getPublishedBy().getId(),
                announcement.getPublishedBy().getDisplayName(),
                roles,
                !announcement.isLiveAt(LocalDateTime.now())
        );
    }

    public static List<AnnouncementResponse> fromAll(List<Announcement> announcements) {
        return announcements.stream().map(AnnouncementResponse::from).toList();
    }
}
