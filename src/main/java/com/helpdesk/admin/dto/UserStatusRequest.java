package com.helpdesk.admin.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body for PATCH /api/admin/users/{id}/status: {"active": false} suspends, true restores.
 * Boolean, not boolean, so a missing field is rejected by @NotNull instead of quietly
 * arriving as false and suspending the account.
 */
public record UserStatusRequest(

        @NotNull(message = "active is required - send {\"active\": true} or {\"active\": false}")
        Boolean active
) {
}
