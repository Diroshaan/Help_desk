package com.helpdesk.ticket.repository;

import com.helpdesk.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    List<Ticket> findByStudentId(Long studentId);

    List<Ticket> findByCategory(String category);

    // A department's queue. Departments are keyed by code, hence String.
    List<Ticket> findByAssignedDepartmentId(String assignedDepartmentId);

    // New tickets not yet routed to any department.
    List<Ticket> findByAssignedDepartmentIdIsNull();
}
