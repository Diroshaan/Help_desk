package com.helpdesk.ticketportal.repository;

import com.helpdesk.ticketportal.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByStudentId(Long studentId);

    Optional<Feedback> findByTicketId(Long ticketId);

    boolean existsByTicketId(Long ticketId);
}
