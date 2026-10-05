package com.helpdesk.admin.controller;

import com.helpdesk.admin.dto.DashboardResponse;
import com.helpdesk.admin.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/admin/dashboard: the live admin dashboard, worked out on each request with no
 * caching. Admin-only through the /api/admin/** rule in SecurityConfig.
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
