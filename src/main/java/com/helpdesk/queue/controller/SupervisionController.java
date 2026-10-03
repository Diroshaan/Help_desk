package com.helpdesk.queue.controller;

import com.helpdesk.queue.dto.OfficerSupervisorResponse;
import com.helpdesk.queue.dto.SupervisorRequest;
import com.helpdesk.queue.service.SupervisionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** F4 - set/read an officer's supervisor. /api/admin/** is already ADMIN-only in SecurityConfig. */
@RestController
@RequestMapping("/api/admin/officers/{officerId}/supervisor")
public class SupervisionController {

    private final SupervisionService supervisionService;

    @Autowired
    public SupervisionController(SupervisionService supervisionService) {
        this.supervisionService = supervisionService;
    }

    @GetMapping
    public OfficerSupervisorResponse get(@PathVariable Long officerId) {
        return OfficerSupervisorResponse.from(supervisionService.get(officerId));
    }

    @PutMapping
    public OfficerSupervisorResponse assign(@PathVariable Long officerId, @RequestBody SupervisorRequest request) {
        return OfficerSupervisorResponse.from(
                supervisionService.assignSupervisor(officerId, request.getSupervisorId()));
    }
}
