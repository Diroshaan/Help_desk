package com.helpdesk.notification.service;

import com.helpdesk.common.user.entity.AppUser;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.profile.entity.Student;

/**
 * The few user details the channels need. Channels get this instead of the entity
 * because listeners run after commit, where lazy loading would fail.
 */
public record NotificationRecipient(Long userId,
                                    String email,
                                    String displayName,
                                    boolean emailEnabled,
                                    boolean portalEnabled) {

    /** Students and officers have their own toggles; admins have none, so they get both channels. */
    public static NotificationRecipient from(AppUser user) {
        boolean email = true;
        boolean portal = true;
        if (user instanceof Student student) {
            email = student.isEmailNotificationsEnabled();
            portal = student.isPortalNotificationsEnabled();
        } else if (user instanceof Officer officer) {
            email = officer.isEmailNotificationsEnabled();
            portal = officer.isPortalNotificationsEnabled();
        }
        return new NotificationRecipient(user.getId(), user.getEmail(), user.getDisplayName(), email, portal);
    }
}
