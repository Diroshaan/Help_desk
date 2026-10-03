package com.helpdesk.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The account endpoints over real HTTP, real security and the real H2 database
 * (F6-N2, F6-N3).
 *
 * The unit tests prove the service's decisions with mocks. This class proves
 * the parts a mock cannot: that @Valid on the controller really turns a missing
 * departmentCodes into a 400 before the service runs, that the departments the
 * seeder created are really found by code, that the JSON the frontend receives
 * has the fields it reads, and that SecurityConfig really keeps a student out.
 *
 * Every test makes its own officer with a unique email and staff number. The
 * Spring context - and so the in-memory database - is shared with every other
 * integration test in the build, so a test that assumed an empty users table,
 * or reused a fixed address, would pass alone and fail in the suite.
 *
 * The caller is the bootstrap administrator, admin@helpdesk.local, which
 * AdminBootstrapSeeder creates when the test context starts (the test profile
 * fixes its password). F6-N2 resolves the caller's email to a real
 * Administrator row, so a made-up admin email would be refused - correctly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAdminIntegrationTest {

    private static final String ADMIN = "admin@helpdesk.local";
    private static final AtomicInteger SEQ = new AtomicInteger(7000);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    private String officerBody(int n, Object departmentCodes) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", "officer" + n + "@helpdesk.local");
        body.put("password", "Secret123");
        body.put("staffNumber", "OF" + n);
        body.put("jobTitle", "Support Officer");
        body.put("fullName", "Officer " + n);
        if (departmentCodes != null) {
            body.put("departmentCodes", departmentCodes);
        }
        return objectMapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("Provisioning an officer records the departments and the provisioning administrator")
    void provisionOfficerWithDepartments() throws Exception {
        int n = SEQ.incrementAndGet();

        mvc.perform(post("/api/admin/officers").with(user(ADMIN).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(officerBody(n, List.of("REG", "IT"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.departmentCodes[0]").value("IT"))
                .andExpect(jsonPath("$.departmentCodes[1]").value("REG"))
                .andExpect(jsonPath("$.provisionedBy").value("System Administrator"));
    }

    // The bug: before F6-N3 this request succeeded, and produced an officer who
    // served nothing. Now it is refused before anything is saved.
    @Test
    @DisplayName("Provisioning an officer with no departments is a 400")
    void provisionOfficerWithoutDepartmentsIsRefused() throws Exception {
        int n = SEQ.incrementAndGet();

        mvc.perform(post("/api/admin/officers").with(user(ADMIN).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(officerBody(n, null)))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/admin/officers").with(user(ADMIN).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(officerBody(n, List.of())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("An unknown department code is a 400 that names the code")
    void unknownDepartmentCodeIsNamed() throws Exception {
        int n = SEQ.incrementAndGet();

        mvc.perform(post("/api/admin/officers").with(user(ADMIN).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(officerBody(n, List.of("ITT"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("ITT")));
    }

    @Test
    @DisplayName("An administrator can change which departments an existing officer serves")
    void updateOfficerDepartments() throws Exception {
        int n = SEQ.incrementAndGet();
        MvcResult created = mvc.perform(post("/api/admin/officers").with(user(ADMIN).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(officerBody(n, List.of("IT"))))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(put("/api/admin/officers/" + id + "/departments").with(user(ADMIN).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentCodes\":[\"REG\",\"FIN\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentCodes.length()").value(2))
                .andExpect(jsonPath("$.departmentCodes[0]").value("FIN"))
                .andExpect(jsonPath("$.departmentCodes[1]").value("REG"));
    }

    @Test
    @DisplayName("A student cannot provision an officer or change an officer's departments (403)")
    void studentIsForbidden() throws Exception {
        int n = SEQ.incrementAndGet();

        mvc.perform(post("/api/admin/officers").with(user("student" + n + "@my.sliit.lk").roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(officerBody(n, List.of("IT"))))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/admin/officers/1/departments").with(user("student" + n + "@my.sliit.lk").roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentCodes\":[\"IT\"]}"))
                .andExpect(status().isForbidden());
    }

    // ---- #48 / F6-N6 part 2 ----

    private long createOfficer(int n) throws Exception {
        MvcResult created = mvc.perform(post("/api/admin/officers").with(user(ADMIN).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(officerBody(n, List.of("IT"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode listUsers(String query) throws Exception {
        MvcResult result = mvc.perform(get("/api/admin/users" + query).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private boolean listContains(JsonNode rows, long id) {
        for (JsonNode row : rows) {
            if (row.get("id").asLong() == id) {
                return true;
            }
        }
        return false;
    }

    private long bootstrapAdminId() throws Exception {
        for (JsonNode row : listUsers("?role=ADMIN")) {
            if (ADMIN.equals(row.get("email").asText())) {
                return row.get("id").asLong();
            }
        }
        throw new AssertionError("the bootstrap administrator should exist in the test database");
    }

    @Test
    @DisplayName("An administrator cannot remove or suspend their own account (400)")
    void administratorCannotLockThemselvesOut() throws Exception {
        long selfId = bootstrapAdminId();

        mvc.perform(delete("/api/admin/users/" + selfId).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("your own account")));

        mvc.perform(patch("/api/admin/users/" + selfId + "/status").with(user(ADMIN).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("your own account")));
    }

    @Test
    @DisplayName("A removed account is hidden by default, listed on request, and cannot be restored")
    void removedAccountIsFinal() throws Exception {
        long id = createOfficer(SEQ.incrementAndGet());

        mvc.perform(delete("/api/admin/users/" + id).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isNoContent());

        assertThat(listContains(listUsers("?role=OFFICER"), id)).isFalse();

        JsonNode withRemoved = listUsers("?role=OFFICER&includeRemoved=true");
        assertThat(listContains(withRemoved, id)).isTrue();
        for (JsonNode row : withRemoved) {
            if (row.get("id").asLong() == id) {
                assertThat(row.get("removed").asBoolean()).isTrue();
                assertThat(row.get("active").asBoolean()).isFalse();
            }
        }

        mvc.perform(patch("/api/admin/users/" + id + "/status").with(user(ADMIN).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("can't be restored")));

        // A repeated DELETE is a harmless 204, not an error.
        mvc.perform(delete("/api/admin/users/" + id).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isNoContent());
    }
}
