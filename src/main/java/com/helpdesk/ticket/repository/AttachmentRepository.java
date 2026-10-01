package com.helpdesk.ticket.repository;

import com.helpdesk.ticket.dto.AttachmentResponse;
import com.helpdesk.ticket.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByTicketId(Long ticketId);

    // A constructor expression selects only the metadata columns, so listing
    // a ticket's files never reads their bytes (up to 5 MB each) just to show
    // names. findByTicketId would load every Attachment, data included.
    @Query("SELECT new com.helpdesk.ticket.dto.AttachmentResponse(a.id, a.ticketId, a.fileName, "
            + "a.fileType, a.fileSize, a.uploadedAt, a.uploadedByUserId, a.kind) FROM Attachment a "
            + "WHERE a.ticketId = :ticketId ORDER BY a.uploadedAt ASC")
    List<AttachmentResponse> findMetadataByTicketId(@Param("ticketId") Long ticketId);
}
