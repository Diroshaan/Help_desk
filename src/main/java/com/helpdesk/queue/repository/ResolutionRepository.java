package com.helpdesk.queue.repository;

import com.helpdesk.queue.entity.Resolution;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ResolutionRepository extends JpaRepository<Resolution, Long> {

    Optional<Resolution> findByTicketId(Long ticketId);
}
