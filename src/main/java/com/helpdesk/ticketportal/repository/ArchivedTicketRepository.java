package com.helpdesk.ticketportal.repository;

import com.helpdesk.ticketportal.entity.ArchivedTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ArchivedTicketRepository extends JpaRepository<ArchivedTicket, Long> {

    List<ArchivedTicket> findByStudentId(Long studentId);

    Optional<ArchivedTicket> findByStudentIdAndTicketId(Long studentId, Long ticketId);

    boolean existsByStudentIdAndTicketId(Long studentId, Long ticketId);

    // Used by StudentTicketQueryService to exclude archived tickets from the
    // default active-view search.
    @Query("SELECT a.ticketId FROM ArchivedTicket a WHERE a.studentId = :studentId")
    List<Long> findTicketIdsByStudentId(@Param("studentId") Long studentId);
}
