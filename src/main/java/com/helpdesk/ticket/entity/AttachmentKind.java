package com.helpdesk.ticket.entity;

/**
 * What an attachment belongs to. RESOLUTION is reserved - resolution files
 * live on the resolution row, so attachments saved here are always SUBMISSION.
 */
public enum AttachmentKind {
    SUBMISSION,
    RESOLUTION
}
