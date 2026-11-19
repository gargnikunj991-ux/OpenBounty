package com.openbounty.service;

import com.openbounty.enums.IdempotencyStatus;
import com.openbounty.exception.BadRequestException;
import com.openbounty.exception.IdempotencyConflictException;
import com.openbounty.exception.IdempotencyPayloadMismatchException;
import com.openbounty.model.IdempotencyRecord;
import com.openbounty.repository.IdempotencyRecordRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service managing distributed idempotency keys, request SHA-256 digests,
 * and deterministic cached response replay.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    public static final int DEFAULT_TTL_HOURS = 24;
    private final IdempotencyRecordRepository idempotencyRepository;

    /**
     * Value object representing the outcome of checking/registering an Idempotency-Key.
     */
    @Getter
    public static class InitiationResult {
        private final boolean replayed;
        private final IdempotencyRecord record;

        private InitiationResult(boolean replayed, IdempotencyRecord record) {
            this.replayed = replayed;
            this.record = record;
        }

        public static InitiationResult replayed(IdempotencyRecord record) {
            return new InitiationResult(true, record);
        }

        public static InitiationResult fresh(IdempotencyRecord record) {
            return new InitiationResult(false, record);
        }
    }

    /**
     * Evaluates an incoming idempotency key against the store.
     * Uses REQUIRES_NEW propagation to ensure the PROCESSING state is committed
     * independently from the downstream controller transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InitiationResult initiateOrRetrieve(String key, String httpMethod, String requestPath, String payload, Long userId) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("Idempotency-Key header cannot be blank.");
        }
        if (key.length() > 128) {
            throw new BadRequestException("Idempotency-Key cannot exceed 128 characters.");
        }

        String requestHash = computeSha256(payload != null ? payload : "");
        LocalDateTime now = LocalDateTime.now();

        Optional<IdempotencyRecord> existingOpt = idempotencyRepository.findByIdempotencyKey(key.trim());

        if (existingOpt.isPresent()) {
            IdempotencyRecord existing = existingOpt.get();

            // Handle expired keys
            if (existing.getExpiresAt().isBefore(now)) {
                log.info("Idempotency key '{}' expired at {}. Purging and creating fresh key.", key, existing.getExpiresAt());
                idempotencyRepository.delete(existing);
                return createProcessingRecord(key.trim(), httpMethod, requestPath, requestHash, userId, now);
            }

            // Verify payload integrity and path consistency
            if (!existing.getRequestHash().equals(requestHash) || !existing.getRequestPath().equals(requestPath)) {
                log.warn("Idempotency payload mismatch for key '{}'. Target path: {}, Stored hash: {}, New hash: {}",
                        key, requestPath, existing.getRequestHash(), requestHash);
                throw new IdempotencyPayloadMismatchException(String.format(
                        "Idempotency key '%s' was previously submitted with a different request payload or target endpoint.", key));
            }

            // Check execution status
            if (existing.getStatus() == IdempotencyStatus.PROCESSING) {
                log.warn("Concurrent duplicate request detected for Idempotency-Key '{}'", key);
                throw new IdempotencyConflictException(String.format(
                        "A request with Idempotency-Key '%s' is currently being processed. Please retry in a few moments.", key));
            }

            if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
                log.info("Idempotency cache hit for key '{}' on path '{}'. Replaying cached response (status {}).",
                        key, requestPath, existing.getResponseStatus());
                return InitiationResult.replayed(existing);
            }

            // Status is FAILED -> allow retry by switching back to PROCESSING
            log.info("Retrying previously failed request for Idempotency-Key '{}'", key);
            existing.setStatus(IdempotencyStatus.PROCESSING);
            existing.setExpiresAt(now.plusHours(DEFAULT_TTL_HOURS));
            IdempotencyRecord updated = idempotencyRepository.save(existing);
            return InitiationResult.fresh(updated);
        }

        return createProcessingRecord(key.trim(), httpMethod, requestPath, requestHash, userId, now);
    }

    private InitiationResult createProcessingRecord(String key, String httpMethod, String requestPath, String requestHash, Long userId, LocalDateTime now) {
        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(key)
                .httpMethod(httpMethod.toUpperCase())
                .requestPath(requestPath)
                .requestHash(requestHash)
                .userId(userId)
                .status(IdempotencyStatus.PROCESSING)
                .expiresAt(now.plusHours(DEFAULT_TTL_HOURS))
                .build();

        try {
            IdempotencyRecord saved = idempotencyRepository.saveAndFlush(record);
            return InitiationResult.fresh(saved);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent insertion race condition for key '{}': {}", key, ex.getMessage());
            throw new IdempotencyConflictException(String.format(
                    "A request with Idempotency-Key '%s' is currently being processed. Please retry in a few moments.", key));
        }
    }

    /**
     * Marks the idempotent execution as successfully completed, storing the response status and body.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String key, int responseStatus, String responseContentType, String responseBody) {
        if (key == null || key.isBlank()) {
            return;
        }

        idempotencyRepository.findByIdempotencyKey(key.trim()).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setResponseStatus(responseStatus);
            record.setResponseContentType(responseContentType);
            record.setResponseBody(responseBody);
            idempotencyRepository.save(record);
            log.debug("Marked Idempotency-Key '{}' as COMPLETED with status {}", key, responseStatus);
        });
    }

    /**
     * Marks the idempotent execution as failed so client retries are permitted.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        idempotencyRepository.findByIdempotencyKey(key.trim()).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.FAILED);
            idempotencyRepository.save(record);
            log.debug("Marked Idempotency-Key '{}' as FAILED", key);
        });
    }

    /**
     * Computes deterministic SHA-256 digest of request payload string.
     */
    public static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest((input != null ? input : "").getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm must be available in JVM", e);
        }
    }
}
