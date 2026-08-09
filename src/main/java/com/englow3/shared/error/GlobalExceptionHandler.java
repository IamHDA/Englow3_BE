package com.englow3.shared.error;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** The only place that decides which HTTP status a broken business rule deserves. */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> onDomainException(DomainException ex) {
        HttpStatus status = switch (ex) {
            case NotFoundException ignored -> HttpStatus.NOT_FOUND;
            case ConflictException ignored -> HttpStatus.CONFLICT;
            case BadRequestException ignored -> HttpStatus.BAD_REQUEST;
            case ForbiddenException ignored -> HttpStatus.FORBIDDEN;
            default -> {
                log.error("No HTTP status mapped for {}", ex.getClass().getName());
                yield HttpStatus.INTERNAL_SERVER_ERROR;
            }
        };
        return ResponseEntity.status(status).body(ApiErrorResponse.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> onValidationFailed(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage()).collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.of("VALIDATION_FAILED", message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> onUploadTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiErrorResponse.of("UPLOAD_TOO_LARGE", "The uploaded file exceeds the size limit"));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> onConcurrentUpdate(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("CONCURRENT_UPDATE", "The resource was modified concurrently, please retry"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> onUnhandled(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_ERROR", "Unexpected error"));
    }
}
