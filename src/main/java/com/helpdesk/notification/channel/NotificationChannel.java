package com.helpdesk.notification.channel;

import com.helpdesk.notification.service.NotificationMessage;
import com.helpdesk.notification.service.NotificationRecipient;

/**
 * STRATEGY PATTERN: the strategy interface.
 *
 * One way of delivering a notification. Today there are two, portal and
 * email (the two channels a student can toggle on their profile), and each
 * lives in its own class that implements this.
 *
 * NotificationService (the context) holds a list of these and never asks
 * which concrete class it has. Adding SMS later means writing one new class
 * that implements this interface and marking it @Component. Nothing else
 * in the system changes. That's the reason for the pattern here: before it,
 * the only way to add a channel would have been another if/else inside the
 * code that decides who gets told what.
 */
public interface NotificationChannel {

    /** Short name used in logs and in the result of NotificationService.notify(). */
    String getName();

    /** Whether this person has this channel switched on. Each channel reads its own toggle. */
    boolean isEnabledFor(NotificationRecipient recipient);

    /** Deliver the message. Only called when isEnabledFor() returned true. */
    void send(NotificationRecipient recipient, NotificationMessage message);
}
