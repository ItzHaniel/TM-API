package com.tmapi.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler — maps domain exceptions to RFC 7807 Problem Details.
 * ProblemDetail (Spring 6+) provides a standardized error response structure.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return problemDetail(HttpStatus.NOT_FOUND, ex.getMessage(), "not-found");
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return problemDetail(HttpStatus.CONFLICT, ex.getMessage(), "conflict");
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex) {
        return problemDetail(HttpStatus.FORBIDDEN, ex.getMessage(), "forbidden");
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedException ex) {
        return problemDetail(HttpStatus.UNAUTHORIZED, ex.getMessage(), "unauthorized");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problemDetail(HttpStatus.FORBIDDEN, "Access denied", "forbidden");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        return problemDetail(HttpStatus.UNAUTHORIZED, ex.getMessage(), "unauthorized");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                (a, b) -> a
            ));

        ProblemDetail pd = problemDetail(HttpStatus.BAD_REQUEST, "Validation failed", "validation-error");
        pd.setProperty("fieldErrors", fieldErrors);
        return pd;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic locking conflict (all retries exhausted): {}", ex.getMessage());
        return problemDetail(HttpStatus.CONFLICT,
            "Resource was modified concurrently. Please retry your request.",
            "optimistic-lock-conflict");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return problemDetail(HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred", "internal-error");
    }

    private ProblemDetail problemDetail(HttpStatus status, String detail, String errorCode) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("https://tmapi.dev/errors/" + errorCode));
        pd.setProperty("timestamp", Instant.now().toString());
        pd.setProperty("errorCode", errorCode);
        return pd;
    }
}
