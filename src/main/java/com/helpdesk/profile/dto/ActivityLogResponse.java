package com.helpdesk.profile.dto;

import com.helpdesk.profile.entity.ActivityLog;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * One entry in the profile page's "Recent activity" list.
 * timestamp is sent already formatted because the page shows it as-is.
 */
public class ActivityLogResponse {

    // fixed locale so month names don't depend on the server's settings
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    private final String timestamp;
    private final String description;

    /** Event name (LOGGED_IN etc.) for code to match on, instead of the description text. */
    private final String type;

    public ActivityLogResponse(String timestamp, String description, String type) {
        this.timestamp = timestamp;
        this.description = description;
        this.type = type;
    }

    public static ActivityLogResponse from(ActivityLog entry) {
        return new ActivityLogResponse(
                entry.getOccurredAt() == null ? "" : entry.getOccurredAt().format(DISPLAY_FORMAT),
                entry.getDescription(),
                entry.getType() == null ? null : entry.getType().name()
        );
    }

    public static List<ActivityLogResponse> fromAll(List<ActivityLog> entries) {
        return entries.stream().map(ActivityLogResponse::from).toList();
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getDescription() {
        return description;
    }

    public String getType() {
        return type;
    }
}
