package com.helpdesk.profile.controller;

import com.helpdesk.profile.dto.OfficerProfileResponse;
import com.helpdesk.profile.dto.OfficerProfileUpdateRequest;
import com.helpdesk.profile.service.OfficerProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in officer's own profile (GET and partial PUT on /api/officers/me).
 * SecurityConfig limits /api/officers/** to officers; getName() is the session email.
 */
@RestController
@RequestMapping("/api/officers/me")
public class OfficerProfileController {

    private final OfficerProfileService officerProfileService;

    public OfficerProfileController(OfficerProfileService officerProfileService) {
        this.officerProfileService = officerProfileService;
    }

    @GetMapping
    public OfficerProfileResponse getProfile(Authentication authentication) {
        return officerProfileService.getProfile(authentication.getName());
    }

    @PutMapping
    public OfficerProfileResponse updateProfile(@Valid @RequestBody OfficerProfileUpdateRequest request,
                                                Authentication authentication) {
        return officerProfileService.updateProfile(authentication.getName(), request);
    }
}
