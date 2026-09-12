package com.helpdesk.queue.repository;

import com.helpdesk.queue.entity.Officer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OfficerRepository extends JpaRepository<Officer, Long> {

    List<Officer> findByDepartmentId(Long departmentId);

    Optional<Officer> findByIdAndActive(Long id, boolean active);
}
