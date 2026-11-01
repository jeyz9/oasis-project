package com.jeyz9.oasisapi.component;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.io.IOException;
import org.slf4j.MDC;

@RestControllerAdvice
public class ApiError {
    public static class Failure extends RuntimeException {
        public final int status;
        public final String code;
        public Failure(int status, String code) { super(code); this.status = status; this.code = code; }
    }
    public static Failure fail(int status, String code) { return new Failure(status, code); }
    public static void write(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code + "\",\"detail\":\"" + code + "\",\"requestId\":\"" + MDC.get("requestId") + "\"}");
    }
    private ResponseEntity<ProblemDetail> problem(int status, String code) {
        var p = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), code);
        p.setProperty("code", code);
        p.setProperty("requestId", MDC.get("requestId"));
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(p);
    }
    @ExceptionHandler(Failure.class)
    ResponseEntity<ProblemDetail> failure(Failure e) { return problem(e.status, e.code); }
    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class, jakarta.validation.ConstraintViolationException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,java.time.DateTimeException.class})
    ResponseEntity<ProblemDetail> invalid(Exception e) { return problem(400, "INVALID_INPUT"); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict(Exception e) { return problem(409, "CONSTRAINT_CONFLICT"); }
    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> busy(Exception e) { return problem(409, "RETRY_TRANSACTION"); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> forbidden(Exception e) { return problem(403, "FORBIDDEN"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e) {
        org.slf4j.LoggerFactory.getLogger(ApiError.class).error("Request {} failed ({})",MDC.get("requestId"),e.getClass().getSimpleName());
        return problem(500,"INTERNAL_ERROR");
    }
}
