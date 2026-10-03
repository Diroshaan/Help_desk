package com.helpdesk.ticketportal.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The request-body rules that turn bad input into a 400 (via @Valid) before
 * it reaches a service: folder colour (F3-N3) and feedback comment length.
 */
class TicketPortalRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static BookmarkFolderRequest folder(String colour) {
        BookmarkFolderRequest request = new BookmarkFolderRequest();
        request.setName("IT issues");
        request.setColour(colour);
        return request;
    }

    @ParameterizedTest
    @ValueSource(strings = {"red", "#zzzzzz", "#12345", "#1234567", "1A2B3C", ""})
    @DisplayName("A colour that isn't #RRGGBB is rejected")
    void badColourIsRejected(String colour) {
        assertThat(validator.validate(folder(colour))).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"#1A2B3C", "#2563eb"})
    @DisplayName("A #RRGGBB colour is accepted")
    void hexColourIsAccepted(String colour) {
        assertThat(validator.validate(folder(colour))).isEmpty();
    }

    @Test
    @DisplayName("No colour at all is fine - renaming a folder doesn't send one")
    void missingColourIsAccepted() {
        assertThat(validator.validate(folder(null))).isEmpty();
    }

    @Test
    @DisplayName("A feedback comment longer than 1000 characters is rejected; 1000 is fine")
    void feedbackCommentLength() {
        FeedbackRequest request = new FeedbackRequest();
        request.setRating(4);

        request.setComment("x".repeat(1000));
        assertThat(validator.validate(request)).isEmpty();

        request.setComment("x".repeat(1001));
        assertThat(validator.validate(request)).isNotEmpty();
    }
}
