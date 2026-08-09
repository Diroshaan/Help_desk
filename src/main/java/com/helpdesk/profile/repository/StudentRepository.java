package com.helpdesk.profile.repository;

import com.helpdesk.profile.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * Data access layer for Student. Extending JpaRepository gives you
 * save(), findById(), findAll(), deleteById() etc. for free - no SQL needed.
 * Add custom query methods here as your stories require them.
 */
public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByStudentId(String studentId);

    Optional<Student> findByEmail(String email);

    boolean existsByStudentId(String studentId);

    boolean existsByEmail(String email);
}
