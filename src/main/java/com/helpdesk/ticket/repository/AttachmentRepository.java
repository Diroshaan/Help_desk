package com.helpdesk.ticket.repository;

import com.helpdesk.ticket.dto.AttachmentResponse;
import com.helpdesk.ticket.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByTicketId(Long ticketId);

    // Selects metadata only, so listing files doesn't load their bytes.
    @Query("SELECT new com.helpdesk.ticket.dto.AttachmentResponse(a.id, a.ticketId, a.fileName, "
            + "a.fileType, a.fileSize, a.uploadedAt, a.uploadedByUserId, a.kind) FROM Attachment a "
            + "WHERE a.ticketId = :ticketId ORDER BY a.uploadedAt ASC")
    List<AttachmentResponse> findMetadataByTicketId(@Param("ticketId") Long ticketId);
}
