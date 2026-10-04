package com.helpdesk.common.reference.repository;

import com.helpdesk.common.reference.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Repository for departments. The key is the String department code. */
public interface DepartmentRepository extends JpaRepository<Department, String> {

    List<Department> findByActiveTrueOrderByNameAsc();
}
