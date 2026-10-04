package org.blr.error;

import java.time.Instant;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiErrorResponse> handleAppException(AppException ex, HttpServletRequest request) {
        HttpStatus status = resolveStatus(ex.getErrorCode());
        return ResponseEntity.status(status).body(buildResponse(request, status, ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        MethodArgumentNotValidException.class,
        BindException.class,
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(Exception ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(buildResponse(request, status, ex.getMessage(), ErrorCode.INVALID_REQUEST));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnhandledException(Exception ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(buildResponse(request, status, ex.getMessage(), ErrorCode.INTERNAL_ERROR));
    }

    private ApiErrorResponse buildResponse(
        HttpServletRequest request,
        HttpStatus status,
        String message,
        ErrorCode errorCode
    ) {
        return new ApiErrorResponse(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            message,
            request.getRequestURI(),
            resolveTraceId(request),
            errorCode
        );
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return traceId;
    }

    private HttpStatus resolveStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case REPOSITORY_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_COMMIT -> HttpStatus.BAD_REQUEST;
            case COMMIT_NOT_ON_MAIN -> HttpStatus.UNPROCESSABLE_ENTITY;
            case GRAPH_BUILD_ALREADY_RUNNING -> HttpStatus.CONFLICT;
            case NODE_PROCESS_START_FAILED -> HttpStatus.BAD_GATEWAY;
            case CODEGRAPH_EXECUTION_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
            case CODEGRAPH_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case GRAPH_VALIDATION_FAILED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case GRAPH_ARTIFACT_PACKAGING_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
            case GRAPH_ARTIFACT_UPLOAD_FAILED -> HttpStatus.BAD_GATEWAY;
            case GRAPH_PUBLICATION_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}