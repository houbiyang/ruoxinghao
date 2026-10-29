package com.weaksignal.vpp.backend.api;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理器。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    /**
     * 注入系统时钟。
     *
     * @param clock 系统时钟
     */
    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    /**
     * 处理业务异常。
     *
     * @param exception 业务异常
     * @return 统一响应
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiResponse.failure(
                        exception.getCode(),
                        exception.getMessage(),
                        exception.getData(),
                        OffsetDateTime.now(clock)
                ));
    }

    /**
     * 处理参数校验异常。
     *
     * @param exception 校验异常
     * @return 统一响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(
                        "VALIDATION_ERROR",
                        "请求参数不合法",
                        errors,
                        OffsetDateTime.now(clock)
                ));
    }

    /**
     * 处理未预期异常。
     *
     * @param exception 未预期异常
     * @return 统一响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpectedException(Exception exception) {
        log.error("处理请求时发生未预期异常", exception);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.failure(
                        "INTERNAL_ERROR",
                        "系统内部错误",
                        Map.of(),
                        OffsetDateTime.now(clock)
                ));
    }
}
