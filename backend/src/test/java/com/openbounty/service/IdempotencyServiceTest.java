package com.openbounty.service;

import com.openbounty.enums.IdempotencyStatus;
import com.openbounty.exception.BadRequestException;
import com.openbounty.exception.IdempotencyConflictException;
import com.openbounty.exception.IdempotencyPayloadMismatchException;
import com.openbounty.model.IdempotencyRecord;
import com.openbounty.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRecordRepository repository;

    @InjectMocks
    private IdempotencyService idempotencyService;

    private final String testKey = "test-key-uuid-12345";
    private final String testMethod = "POST";
    private final String testPath = "/api/bounties";
    private final String testPayload = "{\"title\":\"Build Auth\"}";
    private String expectedHash;

    @BeforeEach
    void setUp() {
        expectedHash = IdempotencyService.computeSha256(testPayload);
    }

    @Test
    @DisplayName("Should create fresh PROCESSING record when key is not present")
    void testInitiateFreshKey() {
        when(repository.findByIdempotencyKey(testKey)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(IdempotencyRecord.class))).thenAnswer(i -> i.getArgument(0));

        IdempotencyService.InitiationResult result = idempotencyService.initiateOrRetrieve(
                testKey, testMethod, testPath, testPayload, 1L);

        assertThat(result.isReplayed()).isFalse();
        assertThat(result.getRecord().getStatus()).isEqualTo(IdempotencyStatus.PROCESSING);
        assertThat(result.getRecord().getIdempotencyKey()).isEqualTo(testKey);
        assertThat(result.getRecord().getRequestHash()).isEqualTo(expectedHash);
        verify(repository, times(1)).saveAndFlush(any(IdempotencyRecord.class));
    }

    @Test
    @DisplayName("Should return replayed record when key is already COMPLETED with identical hash")
    void testReplayCompletedKey() {
        IdempotencyRecord existing = IdempotencyRecord.builder()
                .idempotencyKey(testKey)
                .httpMethod(testMethod)
                .requestPath(testPath)
                .requestHash(expectedHash)
                .status(IdempotencyStatus.COMPLETED)
                .responseStatus(201)
                .responseBody("{\"id\":10}")
                .expiresAt(LocalDateTime.now().plusHours(12))
                .build();

        when(repository.findByIdempotencyKey(testKey)).thenReturn(Optional.of(existing));

        IdempotencyService.InitiationResult result = idempotencyService.initiateOrRetrieve(
                testKey, testMethod, testPath, testPayload, 1L);

        assertThat(result.isReplayed()).isTrue();
        assertThat(result.getRecord().getResponseStatus()).isEqualTo(201);
        assertThat(result.getRecord().getResponseBody()).isEqualTo("{\"id\":10}");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException when key is currently in PROCESSING status")
    void testConflictOnProcessingKey() {
        IdempotencyRecord existing = IdempotencyRecord.builder()
                .idempotencyKey(testKey)
                .httpMethod(testMethod)
                .requestPath(testPath)
                .requestHash(expectedHash)
                .status(IdempotencyStatus.PROCESSING)
                .expiresAt(LocalDateTime.now().plusHours(12))
                .build();

        when(repository.findByIdempotencyKey(testKey)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.initiateOrRetrieve(
                testKey, testMethod, testPath, testPayload, 1L))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("currently being processed");
    }

    @Test
    @DisplayName("Should throw IdempotencyPayloadMismatchException when key reused with different payload")
    void testPayloadMismatch() {
        IdempotencyRecord existing = IdempotencyRecord.builder()
                .idempotencyKey(testKey)
                .httpMethod(testMethod)
                .requestPath(testPath)
                .requestHash(IdempotencyService.computeSha256("{\"title\":\"Different Title\"}"))
                .status(IdempotencyStatus.COMPLETED)
                .expiresAt(LocalDateTime.now().plusHours(12))
                .build();

        when(repository.findByIdempotencyKey(testKey)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.initiateOrRetrieve(
                testKey, testMethod, testPath, testPayload, 1L))
                .isInstanceOf(IdempotencyPayloadMismatchException.class)
                .hasMessageContaining("different request payload");
    }

    @Test
    @DisplayName("Should handle concurrent database insert race condition with conflict exception")
    void testConcurrentInsertConflict() {
        when(repository.findByIdempotencyKey(testKey)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key violation"));

        assertThatThrownBy(() -> idempotencyService.initiateOrRetrieve(
                testKey, testMethod, testPath, testPayload, 1L))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("currently being processed");
    }

    @Test
    @DisplayName("Should validate blank and oversized keys")
    void testInvalidKeyValidation() {
        assertThatThrownBy(() -> idempotencyService.initiateOrRetrieve(
                "", testMethod, testPath, testPayload, 1L))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> idempotencyService.initiateOrRetrieve(
                "A".repeat(129), testMethod, testPath, testPayload, 1L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Should mark completed successfully")
    void testComplete() {
        IdempotencyRecord existing = IdempotencyRecord.builder()
                .idempotencyKey(testKey)
                .status(IdempotencyStatus.PROCESSING)
                .build();

        when(repository.findByIdempotencyKey(testKey)).thenReturn(Optional.of(existing));

        idempotencyService.complete(testKey, 201, "application/json", "{\"status\":\"ok\"}");

        assertThat(existing.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(existing.getResponseStatus()).isEqualTo(201);
        assertThat(existing.getResponseBody()).isEqualTo("{\"status\":\"ok\"}");
        verify(repository, times(1)).save(existing);
    }
}
