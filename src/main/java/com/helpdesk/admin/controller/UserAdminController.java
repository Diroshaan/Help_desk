package com.helpdesk.admin.controller;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * F6 - System Analytics, Provisioning & Announcements
 *
 * Account provisioning and lifecycle (WBHD-35).
 *
 *   POST   /api/admin/officers            -> 201 create an officer account
 *   POST   /api/admin/administrators      -> 201 create an admin account
 *   GET    /api/admin/users               -> 200 all accounts, ?role= to filter
 *   PATCH  /api/admin/users/{id}/status   -> 200 {"active": false} suspend/restore
 *   DELETE /api/admin/users/{id}          -> 204 soft delete
 *
 * All five are under /api/admin/**, already hasRole("ADMIN") in SecurityConfig.
 * No security change is needed and nothing here re-checks the role.
 *
 * The class has no @RequestMapping prefix because the five paths do not share
 * one - three roots, three resources. Forcing a common prefix would mean bending
 * a URL into a shape that misdescribes what it returns, which is the same
 * reasoning ReferenceDataController gives for the same choice.
 *
 * Every response is a UserSummaryResponse and never an entity. Administrator,
 * Officer and AppUser all carry the BCrypt password hash; the DTO has no
 * password field at all, so the protection is structural rather than one
 * annotation somebody can delete.
 */
@RestController
public class UserAdminController {

    private final UserProvisioningService userProvisioningService;

    @Autowired
    public UserAdminController(UserProvisioningService userProvisioningService) {
        this.userProvisioningService = userProvisioningService;
    }

    /**
     * Provision a help desk officer. 201 with a Location header pointing at the
     * new account in the listing.
     *
     * The Authentication parameter is the only source of the provisioning
     * administrator's identity. ProvisionOfficerRequest has no provisionedBy
     * field - see the comment at the top of that class - so there is no way for
     * a caller to claim somebody else created the account. Spring supplies this
     * parameter from the security context; it is not bound from the request, and
     * there is no annotation on it for a client to influence.
     *
     * authentication.getName() is the EMAIL the caller signed in with, because
     * StudentUserDetailsService builds every UserDetails with
     * .username(user.getEmail()) regardless of whether the person typed their
     * student ID or their address. That is what makes findByEmail the right
     * lookup in the service.
     *
     * Worth reading alongside setStatus below, which explains why it REMOVED its
     * Authentication parameter. That one existed to enforce a rule that no
     * longer exists. This one exists because a record of who acted IS the
     * requirement, and the session is the only trustworthy place to get it from.
     */
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

    /**
     * Provision a system administrator.
     *
     * Same rule as above: the creating administrator comes from the session, not
     * the body. It matters slightly more here, because the account being created
     * can itself provision accounts - an unrecorded chain of administrators
     * creating administrators is exactly what the audit requirement exists to
     * prevent.
     */
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

    /**
     * Every account of every type, newest first, optionally narrowed to one
     * role with ?role=OFFICER.
     *
     * A query parameter rather than /api/admin/users/officers, because this IS a
     * filter applied to one collection - all three types live in one table and
     * one listing screen. A path segment would claim officers are a separate
     * collection with its own identity, and would then owe an answer to "what
     * does /api/admin/users/officers/{id} mean when the id is a student's?".
     *
     * required = false so the bare /api/admin/users returns everything.
     * Spring converts the string to the Role enum automatically; an unknown
     * value produces a 400 rather than silently matching nothing, which is the
     * honest response to ?role=OFICER.
     */
    @GetMapping("/api/admin/users")
    public ResponseEntity<List<UserSummaryResponse>> findAll(
            @RequestParam(name = "role", required = false) Role role) {

        return ResponseEntity.ok(userProvisioningService.findAll(role));
    }

    /**
     * Suspend or restore an account: {"active": false} or {"active": true}.
     *
     * PATCH, not PUT: one attribute changes and the rest of the account is left
     * alone. A PUT would mean "here is the whole resource, replace it", which
     * would require the client to send back every field it did not want to
     * change - and every one of those is a field it could get wrong.
     *
     * No Authentication parameter, deliberately. This method used to pass the
     * caller's email down so the service could refuse an administrator
     * deactivating themselves. That rule is gone, replaced by the single
     * invariant "the system must always have an active administrator", which
     * does not depend on who is asking - see UserProvisioningService.setActive
     * for why one invariant beats two overlapping ones. A parameter kept only
     * because it used to be needed is a parameter the next reader has to work
     * out the purpose of, so it is removed rather than left dangling.
     *
     * The invariant is enforced in the service, not here, because it is a fact
     * about the state of the system rather than about HTTP. It surfaces as a 400
     * through GlobalExceptionHandler.
     */
    @PatchMapping("/api/admin/users/{id}/status")
    public ResponseEntity<UserSummaryResponse> setStatus(@PathVariable Long id,
                                                         @Valid @RequestBody UserStatusRequest request) {
        return ResponseEntity.ok(userProvisioningService.setActive(id, request.active()));
    }

    /**
     * SOFT delete - the row stays, active becomes false.
     *
     * Never a hard row delete. Tickets, bookmarks, feedback and activity-log
     * rows all point at user ids, and removing the row destroys the history of
     * every ticket that person ever handled. A deactivated account cannot log
     * in, because StudentUserDetailsService builds the UserDetails with
     * .disabled(!isActive()) - so from the user's side this is indistinguishable
     * from deletion, while the history survives.
     *
     * 204 No Content: it worked and there is nothing to return.
     *
     * Worth knowing that DELETE here is not idempotent in the strictest sense -
     * deleting the last active administrator is refused with a 400 whether it is
     * the first attempt or the fifth. That is the right trade: HTTP's idempotency
     * expectation is about repeated requests having the same EFFECT, and
     * refusing consistently satisfies that better than locking the deployment
     * out of its own admin panel would.
     */
    @DeleteMapping("/api/admin/users/{id}")
    public ResponseEntity<Void> softDelete(@PathVariable Long id) {
        userProvisioningService.softDelete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}