package com.helpdesk.profile.controller;

import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * REST endpoints for F1 - Student Profile & Preferences Management.
 * Keep controllers thin: validate the request shape, call the service, return a response.
 * All the actual logic lives in StudentService.
 */
@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;

    @Autowired
    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    // POST /api/students  -> register (US-03)
    @PostMapping
    public ResponseEntity<Student> register(@Valid @RequestBody Student student) {
        Student saved = studentService.register(student);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // GET /api/students -> list (mainly for admin/testing during development)
    @GetMapping
    public List<Student> findAll() {
        return studentService.findAll();
    }

    // GET /api/students/{id} -> profile dashboard
    @GetMapping("/{id}")
    public ResponseEntity<Student> findById(@PathVariable Long id) {
        return studentService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // PUT /api/students/{id} -> edit profile (US-01)
    @PutMapping("/{id}")
    public ResponseEntity<Student> updateProfile(@PathVariable Long id, @Valid @RequestBody Student updatedDetails) {
        return ResponseEntity.ok(studentService.updateProfile(id, updatedDetails));
    }

    // DELETE /api/students/{id} -> self-service deactivation (US-02)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        studentService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
