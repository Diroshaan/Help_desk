package com.helpdesk.ticketportal.repository;

import com.helpdesk.ticketportal.entity.BookmarkFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookmarkFolderRepository extends JpaRepository<BookmarkFolder, Long> {

    List<BookmarkFolder> findByStudentId(Long studentId);

    boolean existsByStudentIdAndNameIgnoreCase(Long studentId, String name);

    boolean existsByStudentIdAndNameIgnoreCaseAndIdNot(Long studentId, String name, Long id);
}
