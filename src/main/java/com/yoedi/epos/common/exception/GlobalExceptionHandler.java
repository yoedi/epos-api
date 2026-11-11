package com.yoedi.epos.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Turns every exception into an RFC 7807 ProblemDetail carrying {@code code} and
 * {@code timestamp}. Spring MVC's own exceptions (bad JSON, unknown route, wrong method, ...)
 * go through {@link #handleExceptionInternal} so they also get a {@code code}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
        ProblemDetail problem = problem(ex.getCode().status(), ex.getTitle(), ex.getDetail(), ex.getCode());
        return ResponseEntity.status(ex.getCode().status()).body(problem);
    }

    // Thrown by @PreAuthorize when the role is not allowed; must not fall through to the 500 handler.
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = problem(HttpStatus.FORBIDDEN, titleFor(ErrorCode.FORBIDDEN),
                detailFor(ErrorCode.FORBIDDEN), ErrorCode.FORBIDDEN);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validasi gagal",
                "Parameter " + ex.getName() + " tidak valid", ErrorCode.VALIDATION_ERROR);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Terjadi kesalahan",
                "Terjadi kesalahan pada server. Silakan coba lagi.", ErrorCode.INTERNAL_ERROR);
        return ResponseEntity.internalServerError().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validasi gagal",
                "Periksa kembali data yang dikirim", ErrorCode.VALIDATION_ERROR);
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = codeFor(statusCode);
        ProblemDetail problem = body instanceof ProblemDetail pd ? pd : ProblemDetail.forStatus(statusCode);
        problem.setTitle(titleFor(code));
        // Spring's default details are English and technical; replace them with a user-facing message.
        problem.setDetail(detailFor(code));
        problem.setProperty("code", code.name());
        problem.setProperty("timestamp", OffsetDateTime.now());
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }

    private Map<String, String> toFieldError(FieldError error) {
        return Map.of(
                "field", toSnakeCase(error.getField()),
                "message", error.getDefaultMessage() == null ? "Tidak valid" : error.getDefaultMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, ErrorCode code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code.name());
        problem.setProperty("timestamp", OffsetDateTime.now());
        return problem;
    }

    // Spring-generated errors have no domain code; pick the closest one from the contract.
    private static ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            default -> status.is4xxClientError() ? ErrorCode.VALIDATION_ERROR : ErrorCode.INTERNAL_ERROR;
        };
    }

    private static String titleFor(ErrorCode code) {
        return switch (code) {
            case UNAUTHORIZED -> "Sesi tidak valid";
            case FORBIDDEN -> "Akses ditolak";
            case NOT_FOUND -> "Data tidak ditemukan";
            case VALIDATION_ERROR -> "Permintaan tidak valid";
            default -> "Terjadi kesalahan";
        };
    }

    private static String detailFor(ErrorCode code) {
        return switch (code) {
            case UNAUTHORIZED -> "Silakan login kembali";
            case FORBIDDEN -> "Anda tidak memiliki akses untuk tindakan ini";
            case NOT_FOUND -> "Alamat yang diminta tidak ditemukan";
            case VALIDATION_ERROR -> "Format permintaan tidak valid";
            default -> "Terjadi kesalahan pada server. Silakan coba lagi.";
        };
    }

    // Field names in error responses follow the JSON naming (snake_case), e.g. sellPrice -> sell_price.
    private static String toSnakeCase(String field) {
        return field.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
