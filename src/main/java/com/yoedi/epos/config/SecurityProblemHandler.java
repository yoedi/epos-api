package com.yoedi.epos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoedi.epos.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;

/**
 * Errors raised inside the security filter chain never reach {@code GlobalExceptionHandler}
 * (that only sees controller exceptions), so they are written here in the same ProblemDetail
 * shape: 401 {@code UNAUTHORIZED} for a missing/invalid token, 403 {@code FORBIDDEN} for a role
 * that is not allowed.
 */
@Component
@RequiredArgsConstructor
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Sesi tidak valid", "Silakan login kembali", ErrorCode.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Akses ditolak",
                "Anda tidak memiliki akses untuk tindakan ini", ErrorCode.FORBIDDEN);
    }

    private void write(HttpServletResponse response, HttpStatus status, String title, String detail,
                       ErrorCode code) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code.name());
        problem.setProperty("timestamp", OffsetDateTime.now());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
