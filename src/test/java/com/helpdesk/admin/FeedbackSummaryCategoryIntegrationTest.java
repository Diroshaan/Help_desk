package com.helpdesk.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/feedback/summary: an unknown category is a 400, not a misleading "0 ratings". */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedbackSummaryCategoryIntegrationTest {

    private static final String ADMIN = "admin@helpdesk.local";

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("An unknown category is a 400 that names it")
    void unknownCategoryIsRefused() throws Exception {
        mvc.perform(get("/api/feedback/summary").param("category", "Nonsense")
                        .with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Nonsense")));
    }

    @Test
    @DisplayName("A blank category is a 400")
    void blankCategoryIsRefused() throws Exception {
        mvc.perform(get("/api/feedback/summary").param("category", "   ")
                        .with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A real category with no ratings is still a 200 with zero ratings")
    void realCategoryIsAccepted() throws Exception {
        // Seeded category with no ratings, so zero is a real answer here.
        mvc.perform(get("/api/feedback/summary").param("category", "Library loans & fines")
                        .with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk());
    }
}
