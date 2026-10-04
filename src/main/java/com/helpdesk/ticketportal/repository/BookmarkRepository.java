package com.helpdesk.ticketportal.repository;

import com.helpdesk.ticketportal.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    List<Bookmark> findByStudentId(Long studentId);

    List<Bookmark> findByStudentIdAndFolderId(Long studentId, Long folderId);

    Optional<Bookmark> findByStudentIdAndTicketId(Long studentId, Long ticketId);

    boolean existsByStudentIdAndTicketId(Long studentId, Long ticketId);

    long countByStudentIdAndFolderId(Long studentId, Long folderId);

    // bookmark count for every folder in one grouped query, instead of one query per folder
    @Query("SELECT b.folderId AS folderId, COUNT(b) AS count " +
            "FROM Bookmark b WHERE b.studentId = :studentId AND b.folderId IS NOT NULL " +
            "GROUP BY b.folderId")
    List<FolderBookmarkCount> countByFolderIdForStudent(@Param("studentId") Long studentId);

    interface FolderBookmarkCount {
        Long getFolderId();
        long getCount();
    }
}
