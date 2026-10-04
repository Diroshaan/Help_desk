package com.helpdesk.knowledgebase.entity;

/** Article lifecycle: draft -> published -> archived. Only PUBLISHED is visible to students. */
public enum ArticleStatus {

    DRAFT,

    PUBLISHED,

    ARCHIVED
}
