package com.helpdesk.profile.service;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.OfficerRepository;
import com.helpdesk.profile.dto.OfficerProfileResponse;
import com.helpdesk.profile.dto.OfficerProfileUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * US-04 - an officer views and updates their own profile and notification
 * preferences.
 *
 * WHY THIS LIVES IN com.helpdesk.profile AND NOT IN queue OR admin
 * -----------------------------------------------------------------
 * The Scrum document assigns US-04 to F1 (tasks T-04.1 to T-04.3), and it is
 * the same feature as the student's profile - "view and update your own
 * details and preferences" - for a different account type. The queue package
 * is F4's workspace for working tickets; admin is F6's for managing OTHER
 * people's accounts. Neither is where a person edits their own profile.
 *
 * WHY THE OFFICER IS FOUND BY THE SESSION'S EMAIL, NOT BY AN ID IN THE URL
 * -----------------------------------------------------------------------
 * The endpoint is /api/officers/me, with no {id}. The student profile takes an
 * id and then has to check it belongs to the caller (StudentController's
 * isOwnProfile); forget that check once and any student can edit any other
 * student - an IDOR. Here there is no id to tamper with. The only officer this
 * code can ever load is the one the session belongs to, so the ownership check
 * cannot be forgotten because there is nothing to check.
 *
 * Both methods return the DTO rather than the entity, built INSIDE the
 * transaction. departments is lazy, and building the response here means the
 * page does not depend on open-session-in-view being switched on.
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
     * Apply a partial update. Every field is optional; a null leaves the
     * stored value untouched (see OfficerProfileUpdateRequest for why).
     *
     * No explicit save() call: the officer was loaded in this transaction, so
     * it is a MANAGED entity and Hibernate writes the changed columns when the
     * transaction commits (dirty checking). Calling save() would work too, but
     * it would suggest to a reader that the object needed re-attaching, which
     * it does not.
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

    /**
     * SecurityConfig already guarantees the caller has the OFFICER role, so an
     * empty result here means the account row disappeared after the session
     * began. 404 says exactly that; it is not a permissions question.
     */
    private Officer findOfficer(String email) {
        return officerRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Officer profile not found"));
    }
}
