package com.helpdesk.queue.repository;

import com.helpdesk.queue.entity.StaffNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StaffNoteRepository extends JpaRepository<StaffNote, Long> {

    List<StaffNote> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
}
