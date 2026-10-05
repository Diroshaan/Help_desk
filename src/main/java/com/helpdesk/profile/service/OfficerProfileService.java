package com.helpdesk.profile.service;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.OfficerRepository;
import com.helpdesk.profile.dto.OfficerProfileResponse;
import com.helpdesk.profile.dto.OfficerProfileUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets an officer view and update their own profile and notification preferences.
 * The officer is found from the session email, not a URL id, so there's no id to tamper with.
 * The DTO is built inside the transaction because departments is lazy.
 */
@Service
public class OfficerProfileService {

    private final OfficerRepository officerRepository;

    public OfficerProfileService(OfficerRepository officerRepository) {
        this.officerRepository = officerRepository;
    }

    @Transactional(readOnly = true)
    public OfficerProfileResponse getProfile(String email) {
        return OfficerProfileResponse.from(findOfficer(email));
    }

    /**
     * Partial update: null fields are left alone. No save() needed, the managed
     * entity is written on commit.
     */
    @Transactional
    public OfficerProfileResponse updateProfile(String email, OfficerProfileUpdateRequest request) {
        Officer officer = findOfficer(email);

        if (request.getFullName() != null) {
            officer.setFullName(request.getFullName().trim());
        }
        if (request.getContactNumber() != null) {
            officer.setContactNumber(request.getContactNumber());
        }
        if (request.getEmailNotificationsEnabled() != null) {
            officer.setEmailNotificationsEnabled(request.getEmailNotificationsEnabled());
        }
        if (request.getPortalNotificationsEnabled() != null) {
            officer.setPortalNotificationsEnabled(request.getPortalNotificationsEnabled());
        }
        return OfficerProfileResponse.from(officer);
    }

    // role is already checked by SecurityConfig, so a miss here means the account is gone (404)
    private Officer findOfficer(String email) {
        return officerRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Officer profile not found"));
    }
}
