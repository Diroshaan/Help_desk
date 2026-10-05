package com.helpdesk.ticket.service;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.ticket.dto.AttachmentResponse;
import com.helpdesk.ticket.entity.Attachment;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.repository.AttachmentRepository;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AttachmentService with mocks: type from the bytes, size limit, ownership and listing rules. */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    private static final Long STUDENT_ID = 7L;
    private static final Long TICKET_ID = 12L;

    private static final byte[] PDF_BYTES = "%PDF-1.7\n%some pdf body".getBytes(StandardCharsets.US_ASCII);

    @Mock private AttachmentRepository attachmentRepository;
    @Mock private TicketService ticketService;

    private AttachmentService service;

    @BeforeEach
    void setUp() {
        service = new AttachmentService(attachmentRepository, ticketService);
    }

    private void ticketIsOwnedAndOpen() {
        when(ticketService.getOwnedOpenTicket(TICKET_ID, STUDENT_ID)).thenReturn(new Ticket());
    }

    // The declared content type is set by the client, so it can't be trusted.
    @Test
    @DisplayName("A real PDF declared as image/png is stored as application/pdf")
    void storesTheDetectedTypeNotTheDeclaredOne() {
        ticketIsOwnedAndOpen();
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "receipt.pdf", "image/png", PDF_BYTES);

        Attachment saved = service.upload(STUDENT_ID, TICKET_ID, file);

        assertThat(saved.getFileType()).isEqualTo("application/pdf");
        assertThat(saved.getData()).isEqualTo(PDF_BYTES);
        assertThat(saved.getTicketId()).isEqualTo(TICKET_ID);
    }

    @Test
    @DisplayName("A text file renamed x.pdf and labelled application/pdf is refused")
    void refusesTextRenamedAsPdf() {
        ticketIsOwnedAndOpen();
        MockMultipartFile file = new MockMultipartFile("file", "x.pdf", "application/pdf",
                "just some plain text, not a pdf".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> service.upload(STUDENT_ID, TICKET_ID, file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Only PDF and image files");
        verify(attachmentRepository, never()).save(any());
    }

    // SVG is an "image" to a browser but is XML that can carry script.
    @Test
    @DisplayName("An SVG labelled image/svg+xml is refused")
    void refusesSvg() {
        ticketIsOwnedAndOpen();
        MockMultipartFile file = new MockMultipartFile("file", "logo.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                        .getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.upload(STUDENT_ID, TICKET_ID, file))
                .isInstanceOf(ValidationException.class);
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("A 6 MB file is refused even if its bytes are a valid PDF")
    void refusesOverFiveMegabytes() {
        ticketIsOwnedAndOpen();
        byte[] big = new byte[6 * 1024 * 1024];
        System.arraycopy(PDF_BYTES, 0, big, 0, PDF_BYTES.length);
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", big);

        assertThatThrownBy(() -> service.upload(STUDENT_ID, TICKET_ID, file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("5MB");
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Uploading to another student's ticket is 'not found', and nothing is saved")
    void refusesSomeoneElsesTicket() {
        when(ticketService.getOwnedOpenTicket(TICKET_ID, STUDENT_ID))
                .thenThrow(new ResourceNotFoundException("Ticket not found"));
        MockMultipartFile file = new MockMultipartFile("file", "receipt.pdf", "application/pdf", PDF_BYTES);

        assertThatThrownBy(() -> service.upload(STUDENT_ID, TICKET_ID, file))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("The client's file name is kept, but a blank one becomes 'attachment'")
    void blankFileNameGetsADefault() {
        ticketIsOwnedAndOpen();
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "", "application/pdf", PDF_BYTES);

        service.upload(STUDENT_ID, TICKET_ID, file);

        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository).save(captor.capture());
        assertThat(captor.getValue().getFileName()).isEqualTo("attachment");
    }

    private AttachmentResponse metadata(Long id) {
        return new AttachmentResponse(id, TICKET_ID, "receipt.pdf", "application/pdf", 42L, LocalDateTime.now(),
                STUDENT_ID, com.helpdesk.ticket.entity.AttachmentKind.SUBMISSION);
    }

    // Listing must use the metadata-only query, not load every file's bytes.
    @Test
    @DisplayName("Listing your own ticket's files returns metadata and never loads the bytes")
    void listOwnTicketReturnsMetadataOnly() {
        when(ticketService.getOwnedTicket(TICKET_ID, STUDENT_ID)).thenReturn(new Ticket());
        when(attachmentRepository.findMetadataByTicketId(TICKET_ID)).thenReturn(List.of(metadata(5L)));

        List<AttachmentResponse> result = service.listByTicket(STUDENT_ID, TICKET_ID);

        assertThat(result).extracting(AttachmentResponse::getId).containsExactly(5L);
        verify(attachmentRepository, never()).findByTicketId(any());
    }

    @Test
    @DisplayName("Listing another student's ticket's files is 'not found'")
    void listSomeoneElsesTicketIsNotFound() {
        when(ticketService.getOwnedTicket(TICKET_ID, STUDENT_ID))
                .thenThrow(new ResourceNotFoundException("Ticket not found"));

        assertThatThrownBy(() -> service.listByTicket(STUDENT_ID, TICKET_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(attachmentRepository, never()).findMetadataByTicketId(any());
    }

    // The queue already scopes the officer, so TicketService (which would refuse an
    // officer) is not asked.
    @Test
    @DisplayName("listForTicket (officer side) returns metadata without a student ownership check")
    void listForTicketSkipsStudentOwnership() {
        when(attachmentRepository.findMetadataByTicketId(TICKET_ID)).thenReturn(List.of(metadata(5L)));

        assertThat(service.listForTicket(TICKET_ID)).hasSize(1);
        verifyNoInteractions(ticketService);
    }

    @Test
    @DisplayName("getForTicket returns the file when it is on that ticket")
    void getForTicketReturnsTheFile() {
        Attachment a = new Attachment();
        a.setId(5L);
        a.setTicketId(TICKET_ID);
        when(attachmentRepository.findById(5L)).thenReturn(Optional.of(a));

        assertThat(service.getForTicket(TICKET_ID, 5L)).isSameAs(a);
    }

    // Access to ticket 12 must not open a file that belongs to ticket 99.
    @Test
    @DisplayName("getForTicket is 'not found' when the attachment is on a different ticket")
    void getForTicketRefusesAnotherTicketsFile() {
        Attachment a = new Attachment();
        a.setId(5L);
        a.setTicketId(99L);
        when(attachmentRepository.findById(5L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.getForTicket(TICKET_ID, 5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // The uploader comes from the session, never the request. kind is always SUBMISSION;
    // RESOLUTION files are stored on the resolution instead.
    @Test
    @DisplayName("An upload records the caller's id as uploader, with kind SUBMISSION")
    void uploadRecordsUploaderAndKind() {
        ticketIsOwnedAndOpen();
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "receipt.pdf", "application/pdf", PDF_BYTES);

        Attachment saved = service.upload(STUDENT_ID, TICKET_ID, file);

        assertThat(saved.getUploadedByUserId()).isEqualTo(STUDENT_ID);
        assertThat(saved.getKind()).isEqualTo(com.helpdesk.ticket.entity.AttachmentKind.SUBMISSION);
    }
}
