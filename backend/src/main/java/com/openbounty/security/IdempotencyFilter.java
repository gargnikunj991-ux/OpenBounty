package com.openbounty.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openbounty.dto.response.ErrorResponse;
import com.openbounty.exception.IdempotencyConflictException;
import com.openbounty.exception.IdempotencyPayloadMismatchException;
import com.openbounty.model.IdempotencyRecord;
import com.openbounty.service.IdempotencyService;
import com.openbounty.service.IdempotencyService.InitiationResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Filter that intercepts mutating HTTP requests bearing an 'Idempotency-Key' header,
 * guaranteeing distributed replay defense, payload integrity verification, and deterministic cached replay.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed";
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String key = request.getHeader(IDEMPOTENCY_KEY_HEADER);

        // Pass through if header is absent or if method is safe/read-only (e.g. GET, HEAD, OPTIONS)
        if (key == null || key.isBlank() || !MUTATING_METHODS.contains(request.getMethod().toUpperCase())) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        Long userId = extractUserId();

        InitiationResult result;
        try {
            result = idempotencyService.initiateOrRetrieve(
                    key,
                    wrappedRequest.getMethod(),
                    wrappedRequest.getRequestURI(),
                    wrappedRequest.getBodyAsString(),
                    userId
            );
        } catch (IdempotencyConflictException ex) {
            log.warn("Idempotency conflict for key '{}': {}", key, ex.getMessage());
            writeRfc7807Error(response, request.getRequestURI(), HttpStatus.CONFLICT,
                    "idempotency-conflict", "Idempotency Conflict", ex.getMessage());
            return;
        } catch (IdempotencyPayloadMismatchException ex) {
            log.warn("Idempotency payload mismatch for key '{}': {}", key, ex.getMessage());
            writeRfc7807Error(response, request.getRequestURI(), HttpStatus.UNPROCESSABLE_ENTITY,
                    "idempotency-payload-mismatch", "Idempotency Payload Mismatch", ex.getMessage());
            return;
        }

        // Fast-path: Replay cached response without hitting controller
        if (result.isReplayed()) {
            IdempotencyRecord record = result.getRecord();
            response.setStatus(record.getResponseStatus() != null ? record.getResponseStatus() : HttpServletResponse.SC_OK);
            response.setHeader(IDEMPOTENT_REPLAYED_HEADER, "true");
            if (record.getResponseContentType() != null) {
                response.setContentType(record.getResponseContentType());
            } else {
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            }
            if (record.getResponseBody() != null) {
                response.getOutputStream().write(record.getResponseBody().getBytes(StandardCharsets.UTF_8));
                response.getOutputStream().flush();
            }
            return;
        }

        // Fresh request: execute downstream pipeline and cache response
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);

            int status = wrappedResponse.getStatus();
            if (status >= 200 && status < 300) {
                byte[] responseBytes = wrappedResponse.getContentAsByteArray();
                String responseBody = new String(responseBytes, StandardCharsets.UTF_8);
                idempotencyService.complete(key, status, wrappedResponse.getContentType(), responseBody);
            } else {
                idempotencyService.markFailed(key);
            }
            wrappedResponse.copyBodyToResponse();
        } catch (Exception ex) {
            idempotencyService.markFailed(key);
            wrappedResponse.copyBodyToResponse();
            throw ex;
        }
    }

    private Long extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        return null;
    }

    private void writeRfc7807Error(HttpServletResponse response,
                                  String requestUri,
                                  HttpStatus status,
                                  String slug,
                                  String title,
                                  String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .type("https://api.openbounty.dev/errors/" + slug)
                .title(title)
                .status(status.value())
                .detail(detail)
                .instance(requestUri)
                .timestamp(LocalDateTime.now())
                .build();

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
        response.getOutputStream().flush();
    }
}
