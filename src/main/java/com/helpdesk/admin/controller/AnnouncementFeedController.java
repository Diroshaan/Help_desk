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
 * F6 - System Analytics, Provisioning & Announcements
 *
 * The notice feed every signed-in user reads.
 *
 *   GET /api/announcements -> 200 the live notices visible to THIS caller
 *
 *
 * WHY THIS PATH IS DELIBERATELY NOT UNDER /api/admin
 * --------------------------------------------------
 * Students and officers read this. Everything under /api/admin/** is
 * hasRole("ADMIN") in SecurityConfig, so putting the feed there would make the
 * announcements unreadable by the people they are addressed to - and the
 * failure would be a 403 on a screen that simply shows nothing, which is a
 * confusing way to find out.
 *
 * This is also why it is a separate controller from AnnouncementAdminController
 * rather than one more method on it: that class is @RequestMapping("/api/admin/
 * announcements"), and a method whose path escapes its own class prefix is the
 * kind of thing a reviewer scanning for admin-only endpoints reads straight
 * past.
 *
 * SECURITY RULE REQUIRED - SEND TO DIROSHAAN, DO NOT EDIT SecurityConfig
 * ---------------------------------------------------------------------
 * This path is not in the permitAll block and does not match any role rule, so
 * it falls through to .anyRequest().authenticated() - which is already the
 * correct level: any logged-in user may read it, no specific role needed, and
 * an anonymous visitor may not. An explicit line makes that intent visible
 * rather than accidental:
 *
 *     .requestMatchers(HttpMethod.GET, "/api/announcements").authenticated()
 *
 * SecurityConfig is a shared file. The line goes to Diroshaan; it is not edited
 * here. The endpoint works either way, which is why this is a request and not a
 * blocker.
 *
 *
 * WHY THE ROLE FILTER IS NOT A QUERY PARAMETER
 * --------------------------------------------
 * The caller's role is resolved from their authenticated account inside the
 * service, never read from the request. A ?role= parameter would let a student
 * ask for the officers' notices simply by typing a different word, which would
 * make requirement 4 - "the set of user roles permitted to view it" - a
 * suggestion rather than a rule.
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
