package com.helpdesk.admin.dto;

import jakarta.validation.constraints.NotNull;

/**
 * F6 - System Analytics, Provisioning & Announcements
 *
 * Request body for PATCH /api/admin/users/{id}/status - suspend or restore an
 * account. One field: {"active": false}.
 *
 * WHY Boolean AND NOT boolean
 * ---------------------------
 * A primitive boolean defaults to false, so a request body that forgot the
 * field - or misspelled it, or sent {} - would silently arrive as "active =
 * false" and deactivate the account. @NotNull cannot catch that, because a
 * primitive is never null. The wrapper type lets "absent" and "false" be
 * different values, and @NotNull then rejects the absent one with a 400 instead
 * of carrying out an action nobody asked for.
 *
 * WHY THIS IS A PATCH AND NOT A PUT
 * ---------------------------------
 * The request changes one attribute and leaves the rest of the account alone.
 * A PUT means "here is the whole resource, replace it", which would require the
 * client to send back every field it did not want to change - and every one of
 * those is a field it could get wrong.
 */
public record UserStatusRequest(

        @NotNull(message = "active is required - send {\"active\": true} or {\"active\": false}")
        Boolean active
) {
}
