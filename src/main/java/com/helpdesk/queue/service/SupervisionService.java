package com.helpdesk.queue.service;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.OfficerRepository;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * F4 - the recursive supervisor relationship (#46): officers.supervisor_id -> officers.id.
 * "At most one supervisor" is the single column; the loop check lives here because
 * SQL cannot express it simply.
 */
@Service
public class SupervisionService {

    private final OfficerRepository officerRepository;

    @Autowired
    public SupervisionService(OfficerRepository officerRepository) {
        this.officerRepository = officerRepository;
    }

    @Transactional(readOnly = true)
    public Officer get(Long officerId) {
        return findOfficer(officerId);
    }

    @Transactional
    public Officer assignSupervisor(Long officerId, Long supervisorId) {
        Officer officer = findOfficer(officerId);

        if (supervisorId == null) {
            officer.setSupervisor(null);
            return officerRepository.save(officer);
        }
        if (supervisorId.equals(officerId)) {
            throw new ValidationException("An officer cannot supervise themselves");
        }

        Officer candidate = officerRepository.findByIdAndActive(supervisorId, true)
                .filter(o -> o.getDeletedAt() == null)
                .orElseThrow(() -> new ValidationException("The supervisor must be an active officer"));

        // Walk upwards from the candidate: reaching this officer means the new
        // link would close a loop.
        for (Officer up = candidate; up != null; up = up.getSupervisor()) {
            if (up.getId().equals(officerId)) {
                throw new ValidationException("That would create a supervision loop");
            }
        }

        officer.setSupervisor(candidate);
        return officerRepository.save(officer);
    }

    private Officer findOfficer(Long officerId) {
        return officerRepository.findById(officerId)
                .orElseThrow(() -> new ResourceNotFoundException("Officer not found"));
    }
}
