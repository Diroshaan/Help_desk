package com.helpdesk.ticket.entity;

/**
 * F2 - what an attachment belongs to (#44, contract C8).
 *
 * RESOLUTION is reserved: resolution files stay on the resolutions row
 * itself (F4 keeps its own columns), not in this table, so every attachment
 * created through this package is SUBMISSION. The enum still carries
 * RESOLUTION so the column's full domain is documented here, even though
 * nothing in F2 ever writes it.
 */
public enum AttachmentKind {
    SUBMISSION,
    RESOLUTION
}
