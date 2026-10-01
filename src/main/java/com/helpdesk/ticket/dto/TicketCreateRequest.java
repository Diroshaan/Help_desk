package com.helpdesk.ticket.dto;

import com.helpdesk.ticket.entity.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for submitting a new ticket. No "studentId" or "status" here -
 * the student is taken from the authenticated session (see
 * TicketController.currentStudentId) and every new ticket starts OPEN,
 * matching the mass-assignment protection used elsewhere (e.g.
 * RegistrationRequest / StudentService.register).
 *
 * The @Size limits mirror Ticket's column lengths, so an over-long value is
 * rejected here with a readable message instead of reaching Hibernate and
 * coming back as a database error.
 */
public class TicketCreateRequest {

    @NotBlank(message = "Subject is required")
    @Size(max = 150, message = "Subject must be 150 characters or fewer")
    private String subject;

    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must be 2000 characters or fewer")
    private String description;

    @NotBlank(message = "Category is required")
    @Size(max = 120, message = "Category must be 120 characters or fewer")
    private String category;

    @NotNull(message = "Priority is required")
    private TicketPriority priority;

    public String getSubject() {
        return subject;
    }
    public void setSubject(String subject) {
        this.subject = subject;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public String getCategory() {
        return category;
    }
    public void setCategory(String category) {
        this.category = category;
    }
    public TicketPriority getPriority() {
        return priority;
    }
    public void setPriority(TicketPriority priority) {
        this.priority = priority;
    }
}
