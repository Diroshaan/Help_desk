package com.helpdesk.knowledgebase.entity;

/**
 * Lifecycle of a knowledge-base article: draft -> publish -> archive.
 *
 * Deliberately three values, not four. Archiving is how an article goes
 * away - a fourth value like DELETED that leaves the row in place would be a
 * lie in the data, and the requirement spec only asks for "draft, tag,
 * publish, edit and archive".
 */
public enum ArticleStatus {

    /** Being written by an officer. Invisible to students. */
    DRAFT,

    /** Live and searchable by students. */
    PUBLISHED,

    /** Withdrawn but kept for the record. Invisible to students, same as DRAFT. */
    ARCHIVED
}
