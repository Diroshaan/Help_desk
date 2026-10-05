package com.helpdesk.admin.dto;

import com.helpdesk.admin.entity.Announcement;
import com.helpdesk.common.user.entity.Role;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Announcement as returned by the API. The publisher is flattened to an id and name so
 * the admin's password hash can never end up in the JSON.
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
     * Must be called inside the service transaction, because publishedBy is lazy.
     * 'expired' is worked out here so clients don't compare timestamps themselves, and
     * roles go into a TreeSet so the JSON order is stable.
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
