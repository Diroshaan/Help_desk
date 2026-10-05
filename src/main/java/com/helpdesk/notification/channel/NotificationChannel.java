package com.helpdesk.notification.channel;

import com.helpdesk.notification.service.NotificationMessage;
import com.helpdesk.notification.service.NotificationRecipient;

/**
 * Strategy interface: one way of delivering a notification (portal or email).
 * NotificationService loops over these without knowing the concrete class, so a new
 * channel (e.g. SMS) is just one more @Component.
 */
public interface NotificationChannel {

    String getName();

    /** Each channel checks its own toggle on the recipient. */
    boolean isEnabledFor(NotificationRecipient recipient);

    /** Only called when isEnabledFor() returned true. */
    void send(NotificationRecipient recipient, NotificationMessage message);
}
