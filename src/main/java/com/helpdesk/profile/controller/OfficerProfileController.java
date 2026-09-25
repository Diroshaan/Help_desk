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
 * US-04 - the officer's own profile.
 *
 *   GET /api/officers/me   view profile, departments and preferences
 *   PUT /api/officers/me   partial update of name, phone and preferences
 *
 * Access: SecurityConfig restricts /api/officers/** to ROLE_OFFICER, so a
 * student or an administrator gets 403 before this class is reached. The
 * controller does no role checking of its own - one rule, in one place.
 *
 * authentication.getName() is the officer's email: every account type signs in
 * with the email as its principal name (see StudentUserDetailsService), so it
 * is the reliable key for "who is this session".
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
