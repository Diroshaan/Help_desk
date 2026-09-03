package com.helpdesk.ticketportal.repository;

import com.helpdesk.ticketportal.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    List<Bookmark> findByStudentId(Long studentId);

    List<Bookmark> findByStudentIdAndFolderId(Long studentId, Long folderId);

    Optional<Bookmark> findByStudentIdAndTicketId(Long studentId, Long ticketId);

    boolean existsByStudentIdAndTicketId(Long studentId, Long ticketId);
}
