package com.helpdesk.profile.service;

import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * Business logic for Student profiles (US-01, US-02, US-03, US-06).
 * Controllers should stay thin and call methods here - this is where
 * validation rules, uniqueness checks, and business decisions live.
 */
@Service
public class StudentService {

    private final StudentRepository studentRepository;

    @Autowired
    public StudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    // Create (US-03: register a new student account)
    public Student register(Student student) {
        if (studentRepository.existsByStudentId(student.getStudentId())) {
            throw new IllegalArgumentException("Student ID already registered");
        }
        if (studentRepository.existsByEmail(student.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }
        return studentRepository.save(student);
    }

    // Read
    public List<Student> findAll() {
        return studentRepository.findAll();
    }

    public Optional<Student> findById(Long id) {
        return studentRepository.findById(id);
    }

    // Update (US-01: edit profile details and notification preferences)
    public Student updateProfile(Long id, Student updatedDetails) {
        Student existing = studentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        existing.setFullName(updatedDetails.getFullName());
        existing.setContactNumber(updatedDetails.getContactNumber());
        existing.setProfilePictureUrl(updatedDetails.getProfilePictureUrl());
        existing.setEmailNotificationsEnabled(updatedDetails.isEmailNotificationsEnabled());
        existing.setPortalNotificationsEnabled(updatedDetails.isPortalNotificationsEnabled());

        return studentRepository.save(existing);
    }

    // Delete (US-02: self-service account deletion - soft delete, not a hard DB delete)
    public void deactivate(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        student.setActive(false);
        studentRepository.save(student);
    }
}
