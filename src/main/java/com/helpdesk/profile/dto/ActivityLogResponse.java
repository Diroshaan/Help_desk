package com.helpdesk.profile.dto;

import com.helpdesk.profile.entity.ActivityLog;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * One activity-log entry as the profile page receives it.
 *
 * The field names are exactly what frontend/src/pages/Profile.jsx already reads
 * in its "Recent activity" section - `entry.timestamp` and `entry.description` -
 * so this feature needs no frontend change at all. That section has been
 * rendering its empty state ("Nothing here yet...") since the React rewrite,
 * waiting for this endpoint to start returning rows.
 *
 * ON SENDING A FORMATTED DATE
 * ---------------------------
 * `timestamp` is a formatted, human-readable string rather than an ISO-8601
 * instant, and that is a compromise rather than a principle. Formatting is a
 * presentation concern and belongs in the client: ISO out of the API, formatted
 * in the browser, so the same data can be rendered in the reader's own locale
 * and timezone. The current page renders whatever it is given verbatim, so
 * sending ISO today would put "2026-09-08T16:19:05.123" on the screen.
 *
 * When the frontend is rebuilt and owns its own date formatting, this should
 * become the ISO value and the pattern below should be deleted. Recording that
 * here so it is a decision with an expiry date rather than an oversight.
 */
public class ActivityLogResponse {

    /**
     * Locale.ENGLISH is passed explicitly rather than relying on the server
     * default. Without it the month name would come from whatever locale the
     * machine running the application happens to have, so the same entry could
     * read differently on a teammate's laptop than on the demo machine - the
     * kind of difference that is invisible until it appears in a screenshot.
     */
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    private final String timestamp;
    private final String description;

    /**
     * The machine-readable event name (ACCOUNT_CREATED, LOGGED_IN, ...).
     *
     * Nothing renders it yet. It is included because `description` is prose
     * written for a human and should never be matched on in code - if the UI
     * later wants an icon per event type, or a filter, this is the field to
     * use, and adding it now costs one line rather than an API change later.
     */
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

    // --- Getters only. Jackson serialises from these. ---

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
