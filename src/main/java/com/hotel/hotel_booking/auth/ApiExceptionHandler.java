package com.hotel.hotel_booking.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static com.hotel.hotel_booking.auth.AuthDtos.ApiError;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> uploadSize(Exception failure){return ResponseEntity.status(413).body(new ApiError("Each photo must be 5 MB or smaller."));}
    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> missingFile(Exception failure){return ResponseEntity.badRequest().body(new ApiError("Choose a JPG or PNG photo to upload."));}
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> authentication(AuthenticationException failure) {
        return ResponseEntity.status(401).body(new ApiError("Email or password is incorrect."));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException failure) {
        return ResponseEntity.badRequest().body(new ApiError("Invalid request. Check the required fields, formats and allowed values."));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> malformed(HttpMessageNotReadableException failure) {
        return ResponseEntity.badRequest().body(new ApiError("Invalid request body."));
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> status(ResponseStatusException failure) {
        return ResponseEntity.status(failure.getStatusCode()).body(new ApiError(failure.getReason()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> conflict(DataIntegrityViolationException failure) {
        return ResponseEntity.status(409).body(new ApiError("Conflicting data. Check for a duplicate email, hotel slug, or room code."));
    }
    @ExceptionHandler({IllegalArgumentException.class, java.time.DateTimeException.class})
    public ResponseEntity<ApiError> badInput(RuntimeException failure) {
        return ResponseEntity.badRequest().body(new ApiError("Invalid request. Check dates, quantities, prices and field formats."));
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> state(IllegalStateException failure) {
        return ResponseEntity.status(409).body(new ApiError("Action conflicts with the current status or availability."));
    }
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiError> forbidden(SecurityException failure) {
        return ResponseEntity.status(403).body(new ApiError("Access denied."));
    }
    @ExceptionHandler({org.springframework.dao.PessimisticLockingFailureException.class, org.springframework.dao.OptimisticLockingFailureException.class})
    public ResponseEntity<ApiError> concurrentChange(RuntimeException failure) {
        return ResponseEntity.status(409).body(new ApiError("Data changed. Please retry with the same requestKey."));
    }
    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class, org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> query(Exception failure) {
        return ResponseEntity.badRequest().body(new ApiError("Invalid or missing query parameter."));
    }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiError> denied(org.springframework.security.access.AccessDeniedException failure) {
        return ResponseEntity.status(403).body(new ApiError("Access denied."));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception failure) {
        org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class).error("Unhandled API request failed", failure);
        return ResponseEntity.status(500).body(new ApiError("The server could not complete this request. Please try again."));
    }
}
