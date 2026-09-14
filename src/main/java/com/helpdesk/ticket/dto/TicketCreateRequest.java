package com.helpdesk.ticket.dto;

import com.helpdesk.ticket.entity.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for submitting a new ticket. No "studentId" or "status" here -
 * the student is taken from the authenticated session (see
 * TicketController.currentStudentId) and every new ticket starts OPEN,
 * matching the mass-assignment protection used elsewhere (e.g.
 * RegistrationRequest / StudentService.register).
 */
public class TicketCreateRequest {

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Description is required")
    private String description;

    @NotBlank(message = "Category is required")
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
