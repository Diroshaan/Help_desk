package com.helpdesk.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who may call what, on the full app: SecurityConfig matchers plus the controllers' ownership checks. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessRulesIntegrationTest {

    @Autowired
    private MockMvc mvc;

    /** Unique Student IDs/emails per registration - the H2 database is shared by every test in the run. */
    private static final AtomicInteger SEQ = new AtomicInteger(1000);

    private long aliceId;
    private long bobId;
    private String aliceEmail;

    @BeforeEach
    void registerTwoStudents() throws Exception {
        aliceEmail = "alice" + SEQ.incrementAndGet() + "@my.sliit.lk";
        aliceId = register(aliceEmail, "Alice Perera");
        bobId = register("bob" + SEQ.incrementAndGet() + "@my.sliit.lk", "Bob Silva");
    }

    // Ownership guard: changing the id in the URL must not reach another student (IDOR).
    @Test
    @DisplayName("A student can read their own profile")
    void studentReadsOwnProfile() throws Exception {
        mvc.perform(get("/api/students/" + aliceId).with(user(aliceEmail).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.givenName").value("Alice"))
                .andExpect(jsonPath("$.surname").value("Perera"));
    }

    @Test
    @DisplayName("A student cannot read another student's profile")
    void studentCannotReadAnother() throws Exception {
        mvc.perform(get("/api/students/" + bobId).with(user(aliceEmail).roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A student cannot edit another student's profile")
    void studentCannotEditAnother() throws Exception {
        mvc.perform(put("/api/students/" + bobId).with(user(aliceEmail).roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Hacked\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A student cannot close another student's account")
    void studentCannotDeleteAnother() throws Exception {
        mvc.perform(delete("/api/students/" + bobId).with(user(aliceEmail).roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A student cannot replace another student's picture")
    void studentCannotUploadForAnother() throws Exception {
        MockMultipartFile png = new MockMultipartFile("file", "a.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D});
        mvc.perform(multipart("/api/students/" + bobId + "/avatar").file(png)
                        .with(user(aliceEmail).roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A student cannot open the officer profile endpoint")
    void studentCannotUseOfficerProfile() throws Exception {
        mvc.perform(get("/api/officers/me").with(user(aliceEmail).roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("An anonymous caller cannot change a password")
    void anonymousCannotChangePassword() throws Exception {
        mvc.perform(put("/api/auth/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"x\",\"newPassword\":\"NewSecret456\"}"))
                .andExpect(status().isForbidden());
    }

    // HEAD must not slip past the GET-only matchers; SecurityConfig denies it on /api/**.
    @Test
    @DisplayName("HEAD cannot be used to get round an officer-only GET rule")
    void headIsDenied() throws Exception {
        mvc.perform(head("/api/articles/manage").with(user(aliceEmail).roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("After a password change the old password stops working and the new one works")
    void passwordChangeEndToEnd() throws Exception {
        MockHttpSession session = login(aliceEmail, "Secret123");

        mvc.perform(put("/api/auth/password").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Secret123\",\"newPassword\":\"NewSecret456\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + aliceEmail + "\",\"password\":\"Secret123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + aliceEmail + "\",\"password\":\"NewSecret456\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A wrong current password is a 400, never a 401 that would sign the user out")
    void wrongCurrentPasswordIs400() throws Exception {
        MockHttpSession session = login(aliceEmail, "Secret123");

        mvc.perform(put("/api/auth/password").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Wrong1234\",\"newPassword\":\"NewSecret456\"}"))
                .andExpect(status().isBadRequest());
    }

    private long register(String email, String fullName) throws Exception {
        String studentId = "IT" + (25000000 + SEQ.incrementAndGet());
        MvcResult result = mvc.perform(post("/api/students").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"" + studentId + "\",\"fullName\":\"" + fullName
                                + "\",\"email\":\"" + email + "\",\"password\":\"Secret123\","
                                + "\"department\":\"Faculty of Computing\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
