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
 * F6 - System Analytics, Provisioning & Announcements
 *
 * Account provisioning and lifecycle (WBHD-35).
 *
 *   POST   /api/admin/officers            -> 201 create an officer account
 *   PUT    /api/admin/officers/{id}/departments -> 200 replace an officer's departments
 *   POST   /api/admin/administrators      -> 201 create an admin account
 *   GET    /api/admin/users               -> 200 accounts, ?role= to filter,
 *                                            ?includeRemoved=true to show removed ones
 *   PATCH  /api/admin/users/{id}/status   -> 200 {"active": false} suspend/restore
 *   DELETE /api/admin/users/{id}          -> 204 remove (final)
 *
 * All six are under /api/admin/**, already hasRole("ADMIN") in SecurityConfig.
 * No security change is needed and nothing here re-checks the role.
 *
 * The class has no @RequestMapping prefix because the six paths do not share
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
     * setStatus and softDelete below take the same parameter for a different
     * reason: they compare it with the target so an administrator cannot lock
     * themselves out. Here it is the record of who acted, which IS the
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
     * Replace the departments an existing officer serves (F6-N3):
     * {"departmentCodes": ["IT", "REG"]}.
     *
     * PUT because the body is the complete new set, not a change to apply - see
     * OfficerDepartmentsRequest. Sending the same body twice gives the same
     * result, which is what PUT promises.
     *
     * Under /api/admin/officers/{id}/... rather than /api/admin/users/{id}/...
     * because departments are a property only an officer has. The path says
     * which kind of account it expects, and an id that is not an officer is a
     * 404 from the service rather than a silent no-op on a student.
     *
     * No SecurityConfig change: /api/admin/** is already ADMIN-only.
     */
    @PutMapping("/api/admin/officers/{id}/departments")
    public ResponseEntity<UserSummaryResponse> updateOfficerDepartments(
            @PathVariable Long id,
            @Valid @RequestBody OfficerDepartmentsRequest request) {

        return ResponseEntity.ok(userProvisioningService.updateOfficerDepartments(id, request));
    }

    /**
     * Every account of every type, optionally narrowed to one role with
     * ?role=OFFICER.
     *
     * A query parameter rather than /api/admin/users/officers, because this IS a
     * filter applied to one collection - all three types live in one table and
     * one listing screen. A path segment would claim officers are a separate
     * collection with its own identity, and would then owe an answer to "what
     * does /api/admin/users/officers/{id} mean when the id is a student's?".
     *
     * includeRemoved defaults to false: removed accounts are history, not people
     * to manage, so they only appear when the screen asks for them.
     *
     * required = false so the bare /api/admin/users returns every non-removed
     * account.
     * Spring converts the string to the Role enum automatically; an unknown
     * value produces a 400 rather than silently matching nothing, which is the
     * honest response to ?role=OFICER.
     */
    @GetMapping("/api/admin/users")
    public ResponseEntity<List<UserSummaryResponse>> findAll(
            @RequestParam(name = "role", required = false) Role role,
            @RequestParam(name = "includeRemoved", defaultValue = "false") boolean includeRemoved) {

        return ResponseEntity.ok(userProvisioningService.findAll(role, includeRemoved));
    }

    /**
     * Suspend or restore an account: {"active": false} or {"active": true}.
     *
     * PATCH, not PUT: one attribute changes and the rest of the account is left
     * alone. A PUT would mean "here is the whole resource, replace it", which
     * would require the client to send back every field it did not want to
     * change - and every one of those is a field it could get wrong.
     *
     * The Authentication parameter is here so the service can refuse an
     * administrator suspending THEMSELVES. This screen is for managing other
     * people, and an admin who locks themselves out needs a second admin to
     * recover. The name comes from the session, never the body, so it cannot be
     * faked.
     *
     * Both rules - not yourself, and not the last active administrator - are
     * enforced in the service, not here, because they are facts about the state
     * of the system rather than about HTTP. They surface as a 400 through
     * GlobalExceptionHandler, as does trying to restore a removed account.
     */
    @PatchMapping("/api/admin/users/{id}/status")
    public ResponseEntity<UserSummaryResponse> setStatus(@PathVariable Long id,
                                                         @Valid @RequestBody UserStatusRequest request,
                                                         Authentication authentication) {
        return ResponseEntity.ok(
                userProvisioningService.setActive(id, request.active(), authentication.getName()));
    }

    /**
     * Remove an account. FINAL: unlike a suspension it cannot be undone with the
     * status toggle.
     *
     * Still never a hard row delete. Tickets, bookmarks, feedback and activity-log
     * rows all point at user ids, and removing the row destroys the history of
     * every ticket that person ever handled. The row stays with deletedAt set
     * (AppUser.markRemoved), and the account cannot sign in.
     *
     * Refused with a 400 for your own account and for the last active
     * administrator. Removing an account that is already removed is a harmless
     * 204, so a retried request after a network blip does not show an error for
     * something that did succeed.
     *
     * 204 No Content: it worked and there is nothing to return.
     */
    @DeleteMapping("/api/admin/users/{id}")
    public ResponseEntity<Void> softDelete(@PathVariable Long id, Authentication authentication) {
        userProvisioningService.softDelete(id, authentication.getName());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}