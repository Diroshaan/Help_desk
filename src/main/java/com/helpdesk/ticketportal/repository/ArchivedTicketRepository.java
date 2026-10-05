package com.helpdesk.ticketportal.repository;

import com.helpdesk.ticketportal.entity.ArchivedTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ArchivedTicketRepository extends JpaRepository<ArchivedTicket, Long> {

    Optional<ArchivedTicket> findByStudentIdAndTicketId(Long studentId, Long ticketId);

    boolean existsByStudentIdAndTicketId(Long studentId, Long ticketId);

    // lets the student ticket list hide archived tickets by default
    @Query("SELECT a.ticketId FROM ArchivedTicket a WHERE a.studentId = :studentId")
    List<Long> findTicketIdsByStudentId(@Param("studentId") Long studentId);
}
