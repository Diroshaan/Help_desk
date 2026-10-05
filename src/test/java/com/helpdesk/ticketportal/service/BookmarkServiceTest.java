package com.helpdesk.ticketportal.service;

import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.service.TicketService;
import com.helpdesk.ticketportal.entity.Bookmark;
import com.helpdesk.ticketportal.entity.BookmarkFolder;
import com.helpdesk.ticketportal.repository.BookmarkFolderRepository;
import com.helpdesk.ticketportal.repository.BookmarkRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** BookmarkService: a student can only bookmark their own ticket, into their own folder. */
@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    private static final Long STUDENT_A = 1L;
    private static final Long STUDENT_B = 2L;
    private static final Long TICKET_ID = 10L;

    @Mock private BookmarkRepository bookmarkRepository;
    @Mock private BookmarkFolderRepository bookmarkFolderRepository;
    @Mock private TicketService ticketService;

    @InjectMocks private BookmarkService bookmarkService;

    @Test
    @DisplayName("Bookmarking someone else's ticket is 'not found', and nothing is saved")
    void otherStudentsTicketIsNotFound() {
        when(ticketService.getOwnedTicket(TICKET_ID, STUDENT_A))
                .thenThrow(new ResourceNotFoundException("Ticket not found"));

        assertThatThrownBy(() -> bookmarkService.createBookmark(STUDENT_A, TICKET_ID, null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(bookmarkRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Bookmarking your own ticket saves it, unfiled")
    void ownTicketIsSaved() {
        when(ticketService.getOwnedTicket(TICKET_ID, STUDENT_A)).thenReturn(new Ticket());
        when(bookmarkRepository.saveAndFlush(any(Bookmark.class))).thenAnswer(inv -> inv.getArgument(0));

        Bookmark saved = bookmarkService.createBookmark(STUDENT_A, TICKET_ID, null);

        assertThat(saved.getStudentId()).isEqualTo(STUDENT_A);
        assertThat(saved.getTicketId()).isEqualTo(TICKET_ID);
        assertThat(saved.getFolderId()).isNull();
    }

    @Test
    @DisplayName("Bookmarking the same ticket twice is a duplicate")
    void twiceIsDuplicate() {
        when(ticketService.getOwnedTicket(TICKET_ID, STUDENT_A)).thenReturn(new Ticket());
        when(bookmarkRepository.existsByStudentIdAndTicketId(STUDENT_A, TICKET_ID)).thenReturn(true);

        assertThatThrownBy(() -> bookmarkService.createBookmark(STUDENT_A, TICKET_ID, null))
                .isInstanceOf(DuplicateResourceException.class);
        verify(bookmarkRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Moving a bookmark into another student's folder is 'not found'")
    void moveIntoOtherStudentsFolderIsNotFound() {
        Bookmark bookmark = new Bookmark();
        bookmark.setId(5L);
        bookmark.setStudentId(STUDENT_A);
        bookmark.setTicketId(TICKET_ID);
        when(bookmarkRepository.findById(5L)).thenReturn(Optional.of(bookmark));

        BookmarkFolder foreignFolder = new BookmarkFolder();
        foreignFolder.setId(20L);
        foreignFolder.setStudentId(STUDENT_B);
        when(bookmarkFolderRepository.findById(20L)).thenReturn(Optional.of(foreignFolder));

        assertThatThrownBy(() -> bookmarkService.moveToFolder(5L, STUDENT_A, 20L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(bookmarkRepository, never()).save(any());
    }

    @Test
    @DisplayName("Losing the race to the unique constraint is still a duplicate (409), not a 500")
    void raceLostAtTheDatabaseIsDuplicate() {
        when(ticketService.getOwnedTicket(TICKET_ID, STUDENT_A)).thenReturn(new Ticket());
        // The exists check passed, but another request inserted first, so the database refuses.
        when(bookmarkRepository.saveAndFlush(any(Bookmark.class)))
                .thenThrow(new DataIntegrityViolationException("uq_bookmark_student_ticket"));

        assertThatThrownBy(() -> bookmarkService.createBookmark(STUDENT_A, TICKET_ID, null))
                .isInstanceOf(DuplicateResourceException.class);
    }
}
