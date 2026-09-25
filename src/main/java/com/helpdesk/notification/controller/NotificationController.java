package com.helpdesk.notification.controller;

import com.helpdesk.notification.dto.NotificationResponse;
import com.helpdesk.notification.service.NotificationInboxService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The portal inbox, for every signed-in role (students, officers and admins
 * all receive notifications). SecurityConfig's catch-all rule already
 * requires a session for /api/notifications/**, so no new rule was needed.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationInboxService inboxService;

    public NotificationController(NotificationInboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping
    public List<NotificationResponse> list(Authentication authentication) {
        return inboxService.listMine(authentication.getName());
    }

    /** Small separate call so the sidebar badge can poll it without fetching the whole list. */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication authentication) {
        return Map.of("unread", inboxService.unreadCount(authentication.getName()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id, Authentication authentication) {
        inboxService.markRead(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        inboxService.markAllRead(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
