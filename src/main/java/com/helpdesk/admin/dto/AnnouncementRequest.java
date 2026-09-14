package com.helpdesk.admin.dto;

import com.helpdesk.common.user.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * F6 - System Analytics, Provisioning & Announcements
 *
 * Request body for creating or editing an announcement.
 *
 * WHAT IS NOT HERE IS THE POINT
 * -----------------------------
 * There is no id, no publishedAt and no publishedBy field. Those three are set
 * by the server, and a request DTO with no field for a value is the
 * anti-mass-assignment control: if the field does not exist, a client that
 * sends {"publishedBy": 1} cannot forge authorship no matter what the service
 * does next. See the comment on profile/dto/RegistrationRequest.java for the
 * attack this prevents - binding an incoming JSON body straight onto an entity
 * lets the client write any column it can guess the name of.
 *
 * The same validation limits as the entity, on purpose. Announcement carries
 * @Size next to every @Column(length), and so does this: an over-length title
 * is rejected at the HTTP boundary with a 400 and a message naming the field,
 * instead of reaching the database and coming back as
 * GlobalExceptionHandler's generic 409.
 *
 * expiresAt is nullable here for the same reason it is nullable on the entity:
 * a standing notice has no end date, and demanding one would mean inventing a
 * date far in the future.
 *
 * visibleToRoles may be null or empty, and both mean the same thing - visible
 * to everybody. See Announcement.isVisibleTo for why that reading was chosen
 * over writing all three roles onto every general notice.
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
