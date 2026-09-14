package com.helpdesk.admin.controller;

import com.helpdesk.admin.dto.DashboardResponse;
import com.helpdesk.admin.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * F6 - System Analytics, Provisioning & Announcements
 *
 * The real-time executive dashboard (WBHD-37).
 *
 *   GET /api/admin/dashboard -> 200
 *
 * One endpoint, one response, computed on the fly. Under /api/admin/**, so
 * hasRole("ADMIN") already applies and no SecurityConfig change is needed.
 *
 * WHY ONE ENDPOINT RATHER THAN ONE PER METRIC
 * -------------------------------------------
 * A dashboard is read as a single picture of the system at one moment. Six
 * endpoints would mean six requests at six slightly different times, so the
 * ticket total could be answered before a ticket is created and the status
 * breakdown after it - and the tiles on the screen would fail to add up,
 * intermittently, in a way nobody can reproduce. The service computes all of it
 * inside one read-only transaction for exactly that reason, and the response
 * carries the generatedAt timestamp those numbers describe.
 *
 * There is no caching here on purpose: the requirement says real-time, and a
 * cached dashboard is a stored metric with a shorter lifetime.
 */
@RestController
public class DashboardController {

    private final DashboardService dashboardService;

    @Autowired
    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/admin/dashboard")
    public ResponseEntity<DashboardResponse> dashboard() {
        return ResponseEntity.ok(dashboardService.buildDashboard());
    }
}
