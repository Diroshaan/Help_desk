package com.helpdesk.ticket.service;

import com.helpdesk.common.exception.ResourceNotFoundException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AttachmentService with its collaborators mocked.
 *
 * Mockito rather than a running application because each rule here is the
 * service's own decision - what type it stores, what it refuses - and a mock
 * lets each test state exactly one situation (a PDF that claims to be a PNG,
 * a text file renamed .pdf) without a database. Ownership is TicketService's
 * decision, so it is mocked to either return the ticket or refuse.
 */
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

    // Why this test exists: the old check trusted file.getContentType(),
    // which the client writes. The stored type must come from the bytes.
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
}
