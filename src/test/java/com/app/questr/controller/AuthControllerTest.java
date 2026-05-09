package com.app.questr.controller;
import com.app.questr.dto.auth.*;
import com.app.questr.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=none","spring.cache.type=none","spring.kafka.listener.auto-startup=false"})
@AutoConfigureMockMvc @Testcontainers
class AuthControllerTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @DynamicPropertySource
    static void ds(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired MockMvc mockMvc;
    // Jackson ObjectMapper is created locally so we don't rely on Spring Boot 4
    // auto-configuration being present in the @AutoConfigureMockMvc test slice.
    private final ObjectMapper om = new ObjectMapper().findAndRegisterModules();
    @Autowired UserRepository userRepo;
    @AfterEach void cleanup() { userRepo.deleteAll(); }
    // ── Signup ─────────────────────────────────────────────────────────
    @Test @DisplayName("POST /signup → 201 + tokens")
    void signup_valid() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(new SignupRequest("hero_a","hero@q.com","pass1234"))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.accessToken").isNotEmpty()).andExpect(jsonPath("$.tokenType").value("Bearer")).andReturn();
        assertThat(om.readValue(r.getResponse().getContentAsString(), AuthResponse.class).userId()).isNotNull();
    }
    @Test @DisplayName("POST /signup duplicate email → 409")
    void signup_dupEmail() throws Exception {
        reg("dup1","dup@q.com","pass1234");
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(new SignupRequest("dup2","dup@q.com","pass1234")))).andExpect(status().isConflict());
    }
    @Test @DisplayName("POST /signup duplicate username → 409")
    void signup_dupUser() throws Exception {
        reg("same","a@q.com","pass1234");
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(new SignupRequest("same","b@q.com","pass1234")))).andExpect(status().isConflict());
    }
    @Test @DisplayName("POST /signup invalid email → 400")
    void signup_badEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(new SignupRequest("hero","not-email","pass1234")))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists());
    }
    @Test @DisplayName("POST /signup short password → 400")
    void signup_shortPw() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(new SignupRequest("hero","h@q.com","short")))).andExpect(status().isBadRequest());
    }
    // ── Login ──────────────────────────────────────────────────────────
    @Test @DisplayName("POST /login with email → 200 + tokens")
    void login_byEmail() throws Exception {
        reg("lhero","login@q.com","pass1234");
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(new LoginRequest("login@q.com","pass1234")))).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
    }
    @Test @DisplayName("POST /login with username → 200")
    void login_byUsername() throws Exception {
        reg("uhero","u@q.com","pass1234");
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(new LoginRequest("uhero","pass1234")))).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /login wrong password → 401")
    void login_wrongPw() throws Exception {
        reg("phero","pw@q.com","correct99");
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(new LoginRequest("pw@q.com","wrongPass")))).andExpect(status().isUnauthorized());
    }
    @Test @DisplayName("POST /login unknown user → 401")
    void login_unknownUser() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(new LoginRequest("ghost@q.com","pass1234")))).andExpect(status().isUnauthorized());
    }
    // ── /me ────────────────────────────────────────────────────────────
    @Test @DisplayName("GET /me without token → 401")
    void me_noToken() throws Exception { mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized()); }
    @Test @DisplayName("GET /me with valid token → 200")
    void me_valid() throws Exception {
        AuthResponse auth = tokens("mher","me@q.com","pass1234");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+auth.accessToken())).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("mher"));
    }
    // ── Refresh ─────────────────────────────────────────────────────────
    @Test @DisplayName("POST /refresh valid → 200 + new tokens")
    void refresh_valid() throws Exception {
        AuthResponse first = tokens("rher","ref@q.com","pass1234");
        MvcResult r = mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(new RefreshTokenRequest(first.refreshToken())))).andExpect(status().isOk()).andReturn();
        assertThat(om.readValue(r.getResponse().getContentAsString(),AuthResponse.class).userId()).isEqualTo(first.userId());
    }
    @Test @DisplayName("POST /refresh with ACCESS token → 401")
    void refresh_wrongType() throws Exception {
        AuthResponse auth = tokens("ther","type@q.com","pass1234");
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(new RefreshTokenRequest(auth.accessToken())))).andExpect(status().isUnauthorized());
    }
    @Test @DisplayName("POST /refresh garbage token → 401")
    void refresh_garbage() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(new RefreshTokenRequest("this.is.garbage")))).andExpect(status().isUnauthorized());
    }
    // ── helpers ─────────────────────────────────────────────────────────
    private void reg(String u, String e, String p) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(new SignupRequest(u,e,p))));
    }
    private AuthResponse tokens(String u, String e, String p) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(new SignupRequest(u,e,p)))).andReturn();
        return om.readValue(r.getResponse().getContentAsString(), AuthResponse.class);
    }
}
