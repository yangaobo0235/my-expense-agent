package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.common.api.ApiErrorResponse;
import com.yangaobo.expense.common.error.ExpenseFlowErrorCode;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ExpenseFlowException.class)
    ResponseEntity<ApiErrorResponse> handleExpenseFlow(
            ExpenseFlowException exception, HttpServletRequest request) {
        String requestId = requestId(request);
        HttpStatus status =
                switch (exception.code()) {
                    case EXPENSE_CASE_NOT_FOUND, POLICY_NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
                    case INVALID_STATE_TRANSITION,
                            OPTIMISTIC_LOCK_CONFLICT,
                            DUPLICATE_REQUEST -> HttpStatus.CONFLICT;
                    case VALIDATION_FAILED, DOCUMENT_REJECTED -> HttpStatus.UNPROCESSABLE_ENTITY;
                    case DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
                    case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
                };
        if (status.is5xxServerError()) {
            LOGGER.error(
                    "Request failed with {}: code={}, path={}, requestId={}",
                    status,
                    exception.code(),
                    request.getRequestURI(),
                    requestId,
                    exception);
        } else if (exception.getCause() != null) {
            LOGGER.warn(
                    "Request rejected with {}: code={}, path={}, requestId={}, cause={}: {}",
                    status,
                    exception.code(),
                    request.getRequestURI(),
                    requestId,
                    exception.getCause().getClass().getName(),
                    exception.getCause().getMessage());
        }
        return ResponseEntity.status(status)
                .body(
                        new ApiErrorResponse(
                                exception.code().name(),
                                exception.getMessage(),
                                requestId,
                                Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        exception
                .getBindingResult()
                .getFieldErrors()
                .forEach(error -> details.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.unprocessableEntity()
                .body(
                        new ApiErrorResponse(
                                ExpenseFlowErrorCode.VALIDATION_FAILED.name(),
                                "Request validation failed",
                                requestId(request),
                                details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        exception
                .getConstraintViolations()
                .forEach(
                        violation ->
                                details.putIfAbsent(
                                        violation.getPropertyPath().toString(),
                                        violation.getMessage()));
        return ResponseEntity.unprocessableEntity()
                .body(
                        new ApiErrorResponse(
                                ExpenseFlowErrorCode.VALIDATION_FAILED.name(),
                                "请求参数校验失败",
                                requestId(request),
                                details));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        String requestId = requestId(request);
        LOGGER.error("Unexpected request failure, requestId={}", requestId, exception);
        return ResponseEntity.internalServerError()
                .body(
                        new ApiErrorResponse(
                                ExpenseFlowErrorCode.INTERNAL_ERROR.name(),
                                "An unexpected error occurred",
                                requestId,
                                Map.of()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponse> handleUploadLimit(
            MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(
                        new ApiErrorResponse(
                                ExpenseFlowErrorCode.DOCUMENT_REJECTED.name(),
                                "Upload exceeds the configured size limit",
                                requestId(request),
                                Map.of()));
    }

    private static String requestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-ID");
        return requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString()
                : requestId;
    }
}
