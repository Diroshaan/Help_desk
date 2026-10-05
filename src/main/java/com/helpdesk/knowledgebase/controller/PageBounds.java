package com.helpdesk.knowledgebase.controller;

/**
 * Clamps page and size request params so a huge or negative value can't cause
 * a lot of work in the search. Shared by both article controllers.
 */
final class PageBounds {

    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 50;
    private static final int MAX_PAGE = 10_000;

    private PageBounds() {
    }

    static int clampSize(int size) {
        return Math.max(MIN_SIZE, Math.min(size, MAX_SIZE));
    }

    static int clampPage(int page) {
        return Math.max(0, Math.min(page, MAX_PAGE));
    }
}
