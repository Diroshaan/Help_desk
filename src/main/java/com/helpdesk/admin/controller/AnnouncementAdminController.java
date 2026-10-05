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
 * Admin management of announcements (create, edit, delete, list). Everything is under
 * /api/admin/**, so SecurityConfig already limits it to ADMIN. @Valid gives a 400 for
 * bad input before it reaches the database.
 */
@RestController
@RequestMapping("/api/admin/announcements")
public class AnnouncementAdminController {

    private final AnnouncementService announcementService;

    @Autowired
    public AnnouncementAdminController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    // The publisher is taken from the logged-in user, never the request body.
    @PostMapping
    public ResponseEntity<AnnouncementResponse> publish(@Valid @RequestBody AnnouncementRequest request,
                                                        Authentication authentication) {
        AnnouncementResponse created = announcementService.publish(request, authentication.getName());
        return ResponseEntity
                .created(URI.create("/api/admin/announcements/" + created.id()))
                .body(created);
    }

    // PUT because the edit form sends the whole announcement back.
    @PutMapping("/{id}")
    public ResponseEntity<AnnouncementResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody AnnouncementRequest request) {
        return ResponseEntity.ok(announcementService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // Includes expired notices, flagged so the UI can grey them out.
    @GetMapping
    public ResponseEntity<List<AnnouncementResponse>> findAll() {
        return ResponseEntity.ok(announcementService.findAllForAdmin());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnnouncementResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(announcementService.findById(id));
    }
}
