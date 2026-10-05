package com.helpdesk.ticketportal.repository;

import com.helpdesk.ticketportal.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    List<Bookmark> findByStudentId(Long studentId);

    List<Bookmark> findByStudentIdAndFolderId(Long studentId, Long folderId);

    boolean existsByStudentIdAndTicketId(Long studentId, Long ticketId);

    long countByStudentIdAndFolderId(Long studentId, Long folderId);
}
