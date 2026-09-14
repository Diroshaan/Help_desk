package com.helpdesk.ticket;

import java.util.List;

/**
 * F2 - Advanced Ticket Request Engine
 *
 * Single source of truth for the set of valid ticket categories, shared by
 * the dropdown endpoint and the create/update validation so they cannot
 * drift apart.
 */
public final class TicketCategories {

    public static final List<String> ALL = List.of(
            "IT Services",
            "Registration",
            "Financial Aid",
            "Library",
            "Hostel",
            "Examinations"
    );

    private TicketCategories() {}

    public static boolean isValid(String category) {
        return ALL.contains(category);
    }
}
