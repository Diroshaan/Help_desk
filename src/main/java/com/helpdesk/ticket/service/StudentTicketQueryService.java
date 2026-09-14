package com.helpdesk.ticket.service;

import com.helpdesk.ticket.dto.TicketSearchCriteria;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketPriority;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticketportal.repository.ArchivedTicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * F2/F4 - lets a student filter and search their own tickets (by status,
 * priority, category, and a free-text keyword against subject/description),
 * with sorting and pagination.
 *
 * Every query is scoped to the requesting student's own tickets - this is
 * not a general-purpose ticket search, it must never let one student see or
 * page through another student's tickets.
 */
@Service
public class StudentTicketQueryService {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("createdAt", "updatedAt", "subject", "category", "priority", "status");

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final TicketRepository ticketRepository;
    private final ArchivedTicketRepository archivedTicketRepository;

    @Autowired
    public StudentTicketQueryService(TicketRepository ticketRepository,
                                      ArchivedTicketRepository archivedTicketRepository) {
        this.ticketRepository = ticketRepository;
        this.archivedTicketRepository = archivedTicketRepository;
    }

    public Page<Ticket> search(Long studentId, TicketSearchCriteria criteria) {
        Specification<Ticket> spec = Specification.where(belongsToStudent(studentId))
                .and(hasStatus(criteria.getStatus()))
                .and(hasPriority(criteria.getPriority()))
                .and(hasCategory(criteria.getCategory()))
                .and(matchesKeyword(criteria.getKeyword()))
                .and(notArchived(studentId));

        return ticketRepository.findAll(spec, toPageable(criteria));
    }

    private Pageable toPageable(TicketSearchCriteria criteria) {
        int page = Math.max(criteria.getPage(), 0);
        int size = criteria.getSize() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(criteria.getSize(), MAX_PAGE_SIZE);
        String sortBy = SORTABLE_FIELDS.contains(criteria.getSortBy()) ? criteria.getSortBy() : "createdAt";
        Sort.Direction direction = "ASC".equalsIgnoreCase(criteria.getSortDirection())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private Specification<Ticket> belongsToStudent(Long studentId) {
        return (root, query, cb) -> cb.equal(root.get("studentId"), studentId);
    }

    private Specification<Ticket> hasStatus(TicketStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private Specification<Ticket> hasPriority(TicketPriority priority) {
        return priority == null ? null : (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    private Specification<Ticket> hasCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return null;
        }
        String normalized = category.trim().toLowerCase();
        return (root, query, cb) -> cb.equal(cb.lower(root.get("category")), normalized);
    }

    private Specification<Ticket> matchesKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String pattern = "%" + escapeLike(keyword.trim().toLowerCase()) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("subject")), pattern, '\\'),
                cb.like(cb.lower(root.get("description")), pattern, '\\'));
    }

    private Specification<Ticket> notArchived(Long studentId) {
        List<Long> archivedIds = archivedTicketRepository.findTicketIdsByStudentId(studentId);
        if (archivedIds.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> cb.not(root.get("id").in(archivedIds));
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
