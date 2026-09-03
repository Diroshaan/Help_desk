package com.helpdesk.ticketportal.repository;

import com.helpdesk.ticketportal.entity.BookmarkFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BookmarkFolderRepository extends JpaRepository<BookmarkFolder, Long> {

    List<BookmarkFolder> findByStudentId(Long studentId);

    Optional<BookmarkFolder> findByStudentIdAndName(Long studentId, String name);

    boolean existsByStudentIdAndNameIgnoreCase(Long studentId, String name);

    boolean existsByStudentIdAndNameIgnoreCaseAndIdNot(Long studentId, String name, Long id);
}
