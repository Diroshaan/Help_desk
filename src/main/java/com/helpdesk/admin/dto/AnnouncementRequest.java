package com.helpdesk.admin.dto;

import com.helpdesk.common.user.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Body for creating or editing an announcement. There are no id, publishedAt or
 * publishedBy fields, so a client can't fake who posted it. expiresAt may be null for a
 * standing notice, and empty visibleToRoles means everyone can see it.
 */
public record AnnouncementRequest(

        @NotBlank(message = "Announcement title is required")
        @Size(max = 200, message = "Title must be 200 characters or fewer")
        String title,

        @NotBlank(message = "Announcement body is required")
        @Size(max = 4000, message = "Body must be 4000 characters or fewer")
        String body,

        LocalDateTime expiresAt,

        Set<Role> visibleToRoles
) {
}
