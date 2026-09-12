package com.helpdesk.ticket.repository;

import com.helpdesk.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByStudentId(Long studentId);

    List<Ticket> findByCategory(String category);

    // F4: a department's queue - every ticket currently routed to it.
    List<Ticket> findByAssignedDepartmentId(Long assignedDepartmentId);
}
