package com.clearchit.exception;

import com.clearchit.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts exceptions into JSON error bodies WITH the matching HTTP status.
 *
 * Previously the handlers returned an ErrorResponse object without setting a
 * status, so every error was sent as HTTP 200 and the frontend treated it as
 * success. Business-rule violations thrown as plain RuntimeException had no
 * handler at all: they became a 500, Spring then forwarded to /error, and
 * because /error required authentication the client received a 401 and was
 * logged out.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            String message,
            Map<String, String> errors) {

        return ResponseEntity
                .status(status)
                .body(new ErrorResponse(status.value(), message, errors));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception) {

        Map<String, String> errors = new HashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(error.getField(), error.getDefaultMessage())
                );

        // Surface the first field message so the UI shows something useful.
        String message = errors.isEmpty()
                ? "Validation failed"
                : errors.values().iterator().next();

        return build(HttpStatus.BAD_REQUEST, message, errors);
    }

    @ExceptionHandler(ChitGroupNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleChitGroupNotFound(
            ChitGroupNotFoundException exception) {

        return build(HttpStatus.NOT_FOUND, exception.getMessage(), null);
    }

    @ExceptionHandler(ChitGroupUpdateNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleUpdateNotAllowed(
            ChitGroupUpdateNotAllowedException exception) {

        return build(HttpStatus.CONFLICT, exception.getMessage(), null);
    }

    @ExceptionHandler(MemberAlreadyInGroupException.class)
    public ResponseEntity<ErrorResponse> handleMemberAlreadyInGroup(
            MemberAlreadyInGroupException exception) {

        return build(HttpStatus.CONFLICT, exception.getMessage(), null);
    }

    @ExceptionHandler(MaxMembersReachedException.class)
    public ResponseEntity<ErrorResponse> handleMaxMembersReached(
            MaxMembersReachedException exception) {

        return build(HttpStatus.CONFLICT, exception.getMessage(), null);
    }

    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMemberNotFound(
            MemberNotFoundException exception) {

        return build(HttpStatus.NOT_FOUND, exception.getMessage(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException exception) {

        return build(HttpStatus.FORBIDDEN, exception.getMessage(), null);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            BadRequestException exception) {

        return build(HttpStatus.BAD_REQUEST, exception.getMessage(), null);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleMalformedRequest(
            Exception exception) {

        return build(
                HttpStatus.BAD_REQUEST,
                "Malformed or missing request data",
                null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException exception) {

        log.warn("Data integrity violation", exception);

        return build(
                HttpStatus.CONFLICT,
                "This request conflicts with existing data "
                        + "(duplicate entry or record still in use).",
                null);
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrency(
            ConcurrencyFailureException exception) {

        log.warn("Concurrent update conflict", exception);

        return build(
                HttpStatus.CONFLICT,
                "Another request is updating the same record. Please retry.",
                null);
    }

    /**
     * Genuine programming/internal faults. These must not be reported as a
     * client error, and their messages (e.g. NullPointerException details)
     * should not be exposed to the caller.
     */
    @ExceptionHandler({
            NullPointerException.class,
            IllegalStateException.class,
            ClassCastException.class,
            HttpMessageNotWritableException.class
    })
    public ResponseEntity<ErrorResponse> handleInternalFault(
            RuntimeException exception) {

        log.error("Internal server error", exception);

        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected server error. Please try again.",
                null);
    }

    /**
     * The service layer reports business-rule violations
     * ("Bidding is not open", "Insufficient wallet balance",
     * "Invalid email or password", ...) as plain RuntimeExceptions.
     * Return them to the client as 400 with their message.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(
            RuntimeException exception) {

        log.warn("Request rejected: {}", exception.getMessage());

        return build(
                HttpStatus.BAD_REQUEST,
                exception.getMessage() != null
                        ? exception.getMessage()
                        : "Request could not be processed",
                null);
    }

    /**
     * Last-resort handler. Spring's own web exceptions (405, 404 for unknown
     * paths, unsupported media type, ...) already know their status, so keep
     * it instead of turning them into a 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {

        if (exception instanceof org.springframework.web.ErrorResponse springError) {

            HttpStatus status = HttpStatus.resolve(
                    springError.getStatusCode().value());

            return build(
                    status != null ? status : HttpStatus.BAD_REQUEST,
                    exception.getMessage(),
                    null);
        }

        log.error("Unexpected server error", exception);

        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected server error. Please try again.",
                null);
    }
}
