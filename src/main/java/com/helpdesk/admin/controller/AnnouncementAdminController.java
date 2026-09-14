package com.helpdesk.admin.controller;

import com.helpdesk.admin.dto.AnnouncementRequest;
import com.helpdesk.admin.dto.AnnouncementResponse;
import com.helpdesk.admin.service.AnnouncementService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * F6 - System Analytics, Provisioning & Announcements
 *
 * Administrator-facing announcement management (WBHD-34, WBHD-36).
 *
 *   POST   /api/admin/announcements       -> 201 create and publish
 *   PUT    /api/admin/announcements/{id}  -> 200 edit
 *   DELETE /api/admin/announcements/{id}  -> 204
 *   GET    /api/admin/announcements       -> 200 all, including expired
 *
 *
 * ACCESS CONTROL
 * --------------
 * Every path here sits under /api/admin/**, which SecurityConfig already
 * restricts with hasRole("ADMIN"). That rule has been in place for weeks with
 * nothing behind it; this is the first feature to use it. No SecurityConfig
 * change is needed for anything in this class, and nothing here re-checks the
 * role - a second check in the controller would be a second place to get it
 * wrong, and the first place is the one Spring Security enforces before the
 * request ever reaches a method here.
 *
 * @Valid on the request bodies is what triggers bean validation. Without it the
 * annotations on AnnouncementRequest are inert - they are read only when
 * something asks for validation - and an empty title would reach the entity and
 * fail at the database instead of returning a 400 that names the field.
 * MethodArgumentNotValidException is mapped to 400 by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/admin/announcements")
public class AnnouncementAdminController {

    private final AnnouncementService announcementService;

    @Autowired
    public AnnouncementAdminController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    /**
     * 201 Created with a Location header, not 200.
     *
     * 201 is the status that means "a new resource now exists", and the Location
     * header says where - so a client can follow it without having to guess the
     * URL from the id in the body. 200 would say "here is a response", which is
     * true of every successful request and tells the client nothing about what
     * just happened.
     *
     * The publisher's identity comes from Authentication, not from the request
     * body. authentication.getName() is the email the caller logged in with;
     * AnnouncementRequest has no publishedBy field precisely so that this is the
     * only possible source. See the comment on AnnouncementService.publish.
     */
    @PostMapping
    public ResponseEntity<AnnouncementResponse> publish(@Valid @RequestBody AnnouncementRequest request,
                                                        Authentication authentication) {
        AnnouncementResponse created = announcementService.publish(request, authentication.getName());
        return ResponseEntity
                .created(URI.create("/api/admin/announcements/" + created.id()))
                .body(created);
    }

    /**
     * PUT rather than PATCH, unlike the account status endpoint.
     *
     * The edit form sends the whole announcement back - title, body, expiry,
     * visibility - so the request really is a complete replacement of the
     * editable state, which is what PUT means. The status endpoint changes one
     * attribute and leaves everything else alone, which is what PATCH means. The
     * two verbs differ here because the two operations genuinely differ, not by
     * accident.
     */
    @PutMapping("/{id}")
    public ResponseEntity<AnnouncementResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody AnnouncementRequest request) {
        return ResponseEntity.ok(announcementService.update(id, request));
    }

    /**
     * 204 No Content: the delete succeeded and there is nothing sensible to
     * return. An empty 200 would leave a client wondering whether a body was
     * meant to be there and got lost.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Every announcement, expired ones included, each flagged with 'expired' so
     * the UI can grey them out. 200 with an empty array when there are none -
     * an empty collection is a truthful answer to "list the announcements",
     * whereas 404 would mean the endpoint itself is missing, which is a
     * different problem.
     */
    @GetMapping
    public ResponseEntity<List<AnnouncementResponse>> findAll() {
        return ResponseEntity.ok(announcementService.findAllForAdmin());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnnouncementResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(announcementService.findById(id));
    }
}
