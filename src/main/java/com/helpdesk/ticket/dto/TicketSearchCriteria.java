package com.helpdesk.ticket.dto;

import com.helpdesk.ticket.entity.TicketPriority;
import com.helpdesk.ticket.entity.TicketStatus;

/**
 * Optional filters for a student's ticket search (F2/F4). Every field left
 * null/blank is simply skipped by StudentTicketQueryService, so an empty
 * criteria (the defaults below) returns every ticket the student owns,
 * newest first.
 */
public class TicketSearchCriteria {

    private TicketStatus status;
    private TicketPriority priority;
    private String category;
    private String keyword;
    private String sortBy = "createdAt";
    private String sortDirection = "DESC";
    private int page = 0;
    private int size = 20;

    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }

    public TicketPriority getPriority() { return priority; }
    public void setPriority(TicketPriority priority) { this.priority = priority; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }

    public String getSortDirection() { return sortDirection; }
    public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
