package com.helpdesk.notification.service;

import com.helpdesk.common.user.entity.AppUser;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.profile.entity.Student;

/**
 * The person a notification is for, reduced to the few things the channels
 * need: who they are, where to email them, and which channels they want.
 *
 * Channels receive this instead of the AppUser entity on purpose. The
 * listeners run after the ticket's transaction has committed, so handing a
 * live entity to a channel would invite LazyInitializationException, and a
 * channel has no business changing a user anyway.
 */
public record NotificationRecipient(Long userId,
                                    String email,
                                    String displayName,
                                    boolean emailEnabled,
                                    boolean portalEnabled) {

    /**
     * Reads the notification preferences from whichever kind of account this is.
     *
     * Students and officers both have the two toggles (F1 sub-function 3 and
     * US-04). Administrators have no preference screen, so they get both
     * channels. An admin being told their password changed is exactly the
     * kind of message nobody should be able to switch off by accident.
     */
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
