package com.openbounty.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openbounty.dto.request.auth.RegisterRequest;
import com.openbounty.enums.Role;
import com.openbounty.model.IdempotencyRecord;
import com.openbounty.enums.IdempotencyStatus;
import com.openbounty.repository.IdempotencyRecordRepository;
import com.openbounty.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IdempotencyRecordRepository idempotencyRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        idempotencyRepository.deleteAll();
    }

    @Test
    @DisplayName("Safe GET requests should bypass idempotency filter even if header is present")
    void testGetBypassesIdempotency() throws Exception {
        mockMvc.perform(get("/api/bounties")
                        .header("Idempotency-Key", "get-key-" + UUID.randomUUID()))
                .andExpect(status().isOk());

        assertThat(idempotencyRepository.count()).isZero();
    }

    @Test
    @DisplayName("Mutating POST without Idempotency-Key executes normally without recording key")
    void testPostWithoutIdempotencyKey() throws Exception {
        String email = "noidem." + UUID.randomUUID() + "@example.com";
        RegisterRequest request = new RegisterRequest("No Idem", email, "SecurePass123!", Role.ROLE_DEVELOPER);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));

        assertThat(idempotencyRepository.count()).isZero();
    }

    @Test
    @DisplayName("Mutating POST with Idempotency-Key records key and replays cached response on second call")
    void testPostWithIdempotencyKeyReplay() throws Exception {
        String key = "idem-key-" + UUID.randomUUID();
        String email = "idem." + UUID.randomUUID() + "@example.com";
        RegisterRequest request = new RegisterRequest("Idem User", email, "SecurePass123!", Role.ROLE_DEVELOPER);
        String payload = objectMapper.writeValueAsString(request);

        // 1. Initial request -> creates record, returns 201 Created
        mockMvc.perform(post("/api/auth/register")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));

        assertThat(idempotencyRepository.count()).isEqualTo(1);
        IdempotencyRecord saved = idempotencyRepository.findByIdempotencyKey(key).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(saved.getResponseStatus()).isEqualTo(201);

        // 2. Exact retry request -> returns 201 Created with cached response and Idempotent-Replayed: true
        // If idempotency failed, this would have thrown 409 Conflict (Duplicate email)
        mockMvc.perform(post("/api/auth/register")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    @DisplayName("Reusing Idempotency-Key with different payload returns 422 Unprocessable Entity")
    void testPayloadMismatchReturns422() throws Exception {
        String key = "idem-key-mismatch-" + UUID.randomUUID();
        String email1 = "mismatch1." + UUID.randomUUID() + "@example.com";
        RegisterRequest req1 = new RegisterRequest("User One", email1, "SecurePass123!", Role.ROLE_DEVELOPER);

        // 1. Initial request
        mockMvc.perform(post("/api/auth/register")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // 2. Second request with same key but different body
        String email2 = "mismatch2." + UUID.randomUUID() + "@example.com";
        RegisterRequest req2 = new RegisterRequest("User Two", email2, "SecurePass123!", Role.ROLE_DEVELOPER);

        mockMvc.perform(post("/api/auth/register")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Idempotency Payload Mismatch"))
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    @DisplayName("Concurrent request arriving while key is in PROCESSING status returns 409 Conflict")
    void testConcurrentProcessingReturns409() throws Exception {
        String key = "idem-key-proc-" + UUID.randomUUID();
        String payload = "{\"name\":\"Concurrent User\"}";

        // Manually persist a PROCESSING record
        IdempotencyRecord processing = IdempotencyRecord.builder()
                .idempotencyKey(key)
                .httpMethod("POST")
                .requestPath("/api/auth/register")
                .requestHash(com.openbounty.service.IdempotencyService.computeSha256(payload))
                .status(IdempotencyStatus.PROCESSING)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        idempotencyRepository.saveAndFlush(processing);

        mockMvc.perform(post("/api/auth/register")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Idempotency Conflict"))
                .andExpect(jsonPath("$.status").value(409));
    }
}
