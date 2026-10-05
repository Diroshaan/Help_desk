package com.helpdesk.admin.controller;

import com.helpdesk.admin.dto.OfficerDepartmentsRequest;
import com.helpdesk.admin.dto.ProvisionAdministratorRequest;
import com.helpdesk.admin.dto.ProvisionOfficerRequest;
import com.helpdesk.admin.dto.UserStatusRequest;
import com.helpdesk.admin.dto.UserSummaryResponse;
import com.helpdesk.admin.service.UserProvisioningService;
import com.helpdesk.common.user.entity.Role;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Admin endpoints for creating officer and admin accounts, listing users, and
 * suspending or removing accounts. All under /api/admin/**, so ADMIN-only.
 * Responses are UserSummaryResponse, never entities, so no password hash goes out.
 */
@RestController
public class UserAdminController {

    private final UserProvisioningService userProvisioningService;

    @Autowired
    public UserAdminController(UserProvisioningService userProvisioningService) {
        this.userProvisioningService = userProvisioningService;
    }

    // Who created the account comes from the session (the caller's email), never the
    // request body, so nobody can credit someone else.
    @PostMapping("/api/admin/officers")
    public ResponseEntity<UserSummaryResponse> provisionOfficer(
            @Valid @RequestBody ProvisionOfficerRequest request,
            Authentication authentication) {

        UserSummaryResponse created =
                userProvisioningService.provisionOfficer(request, authentication.getName());
        return ResponseEntity
                .created(URI.create("/api/admin/users/" + created.id()))
                .body(created);
    }

    // Same rule as above: the creating admin is taken from the session.
    @PostMapping("/api/admin/administrators")
    public ResponseEntity<UserSummaryResponse> provisionAdministrator(
            @Valid @RequestBody ProvisionAdministratorRequest request,
            Authentication authentication) {

        UserSummaryResponse created =
                userProvisioningService.provisionAdministrator(request, authentication.getName());
        return ResponseEntity
                .created(URI.create("/api/admin/users/" + created.id()))
                .body(created);
    }

    // Replaces the officer's whole department set. An id that isn't an officer gives 404.
    @PutMapping("/api/admin/officers/{id}/departments")
    public ResponseEntity<UserSummaryResponse> updateOfficerDepartments(
            @PathVariable Long id,
            @Valid @RequestBody OfficerDepartmentsRequest request) {

        return ResponseEntity.ok(userProvisioningService.updateOfficerDepartments(id, request));
    }

    // Optional ?role= filter; an unknown role is a 400. Removed accounts are hidden
    // unless includeRemoved=true.
    @GetMapping("/api/admin/users")
    public ResponseEntity<List<UserSummaryResponse>> findAll(
            @RequestParam(name = "role", required = false) Role role,
            @RequestParam(name = "includeRemoved", defaultValue = "false") boolean includeRemoved) {

        return ResponseEntity.ok(userProvisioningService.findAll(role, includeRemoved));
    }

    /**
     * Suspend or restore an account. The service refuses (400) if an admin targets
     * themselves, if it would leave no active admin, or if the account was removed.
     */
    @PatchMapping("/api/admin/users/{id}/status")
    public ResponseEntity<UserSummaryResponse> setStatus(@PathVariable Long id,
                                                         @Valid @RequestBody UserStatusRequest request,
                                                         Authentication authentication) {
        return ResponseEntity.ok(
                userProvisioningService.setActive(id, request.active(), authentication.getName()));
    }

    /**
     * Removes an account for good (soft delete: the row stays with deletedAt set so
     * ticket history is kept). Same self/last-admin checks as above. Removing an
     * already removed account just returns 204, so a retry doesn't show an error.
     */
    @DeleteMapping("/api/admin/users/{id}")
    public ResponseEntity<Void> softDelete(@PathVariable Long id, Authentication authentication) {
        userProvisioningService.softDelete(id, authentication.getName());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}