package com.helpdesk.knowledgebase.controller;

/**
 * F5-N3: {@code page}/{@code size} arrived at {@link ArticleController} and
 * {@link ArticleAdminController} as raw, unvalidated request parameters -
 * {@code size=2000000} or {@code page=-1} reached {@code PageRequest.of()}
 * directly, and large values there mean large in-memory work further down
 * (ArticleSearchService materialises its whole intersected result set before
 * paging it). Clamping here, before a Pageable is even constructed, is
 * cheaper to reason about than trying to bound it after the fact in the
 * service.
 *
 * Package-private and shared rather than duplicated per controller - both
 * endpoints need the exact same bounds, and two copies of the same two-line
 * method is the kind of duplication that drifts the next time one of them
 * gets edited and the other doesn't.
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
