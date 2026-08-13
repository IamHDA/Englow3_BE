package com.englow3.shared.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.englow3.shared.logging.TraceIdFilter;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** The only place that decides which HTTP status a broken business rule deserves. */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> onDomainException(DomainException ex) {
        HttpStatus status = switch (ex) {
            case NotFoundException ignored -> HttpStatus.NOT_FOUND;
            case ConflictException ignored -> HttpStatus.CONFLICT;
            case BadRequestException ignored -> HttpStatus.BAD_REQUEST;
            case ForbiddenException ignored -> HttpStatus.FORBIDDEN;
        };
        return ResponseEntity.status(status).body(error(ex.getCode(), ex.getMessage()));
    }

    /** A @PreAuthorize refusal. Without this handler the catch-all swallows it and answers 500. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> onAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("ACCESS_DENIED", "You are not allowed to perform this action"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> onDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error("DATA_CONFLICT", "The operation conflicts with existing data"));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> onConcurrentUpdate(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error("CONCURRENT_UPDATE", "The resource was modified concurrently, please retry"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> onUnhandled(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("INTERNAL_ERROR", "Unexpected error"));
    }

    /** One entry per field so the frontend can highlight the offending input. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(err -> {
            String field = err instanceof FieldError fieldError ? fieldError.getField() : err.getObjectName();
            fieldErrors.putIfAbsent(field, err.getDefaultMessage());
        });
        ApiErrorResponse payload = ApiErrorResponse.withFields("VALIDATION_FAILED", "Some fields are invalid",
                fieldErrors, TraceIdFilter.current());
        return handleExceptionInternal(ex, payload, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Where the body of every standard Spring MVC exception becomes an ApiErrorResponse - Spring keeps the status. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("Spring MVC exception", ex);
        } else {
            log.warn("Client error {} - {}: {}", statusCode.value(), ex.getClass().getSimpleName(), ex.getMessage());
        }
        Object payload = body instanceof ApiErrorResponse ? body : error(codeFor(statusCode), reasonFor(statusCode));
        return super.handleExceptionInternal(ex, payload, headers, statusCode, request);
    }

    /** BAD_REQUEST, METHOD_NOT_ALLOWED, UNSUPPORTED_MEDIA_TYPE... - stable enough for the frontend to switch on. */
    private String codeFor(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status == null ? "REQUEST_FAILED" : status.name();
    }

    /** The standard reason phrase - never ex.getMessage(), which leaks class and package names to the client. */
    private String reasonFor(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status == null ? "The request could not be processed" : status.getReasonPhrase();
    }

    /** Every error response carries the traceId of the current request. */
    private ApiErrorResponse error(String code, String message) {
        return ApiErrorResponse.of(code, message, TraceIdFilter.current());
    }
}
