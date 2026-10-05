package com.helpdesk.admin.controller;

import com.helpdesk.admin.dto.AnnouncementResponse;
import com.helpdesk.admin.service.AnnouncementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /api/announcements: the live notices for the signed-in user. Kept out of
 * /api/admin because students and officers need to read it; any logged-in user may.
 * The role comes from the caller's account, not a request parameter, so nobody can ask
 * for another role's notices.
 */
@RestController
public class AnnouncementFeedController {

    private final AnnouncementService announcementService;

    @Autowired
    public AnnouncementFeedController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping("/api/announcements")
    public ResponseEntity<List<AnnouncementResponse>> liveForCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(announcementService.findLiveFor(authentication.getName()));
    }
}
