package com.helpdesk.knowledgebase.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB requirement: every article needs at least one category (F5 gap, fixed
 * by @NotEmpty on ArticleRequest.categoryIds). Validates the DTO directly
 * with a plain jakarta.validation Validator rather than through MockMvc -
 * this confirms the annotation fires; it does not confirm
 * GlobalExceptionHandler's exact 400 response shape, which is a controller-
 * level concern this test isn't making a claim about either way.
 */
class ArticleRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        factory.close();
    }

    @Test
    void emptyCategoryIds_failsValidation_namingCategoryIds() {
        ArticleRequest request = new ArticleRequest("Title", "Body text", Set.of("faq"), Set.of());

        Set<ConstraintViolation<ArticleRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("categoryIds");
    }

    @Test
    void nullCategoryIds_alsoFailsValidation_becauseTheCompactConstructorNormalisesToEmpty() {
        ArticleRequest request = new ArticleRequest("Title", "Body text", Set.of("faq"), null);

        Set<ConstraintViolation<ArticleRequest>> violations = validator.validate(request);

        // Without the record's compact constructor turning a null
        // categoryIds into Set.of(), @NotEmpty alone would treat null as
        // valid (Bean Validation's usual null-is-valid rule) and only an
        // explicit [] in the request body would be rejected - a missing
        // field would slip through.
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("categoryIds");
    }

    @Test
    void atLeastOneCategory_passesValidation() {
        ArticleRequest request = new ArticleRequest("Title", "Body text", Set.of("faq"), Set.of(1L));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void moreThanFiveCategories_failsValidation() {
        ArticleRequest request = new ArticleRequest(
                "Title", "Body text", Set.of("faq"), Set.of(1L, 2L, 3L, 4L, 5L, 6L));

        Set<ConstraintViolation<ArticleRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("categoryIds");
    }
}
