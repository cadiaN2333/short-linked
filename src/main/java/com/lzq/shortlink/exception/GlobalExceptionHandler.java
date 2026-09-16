package com.lzq.shortlink.exception;

import com.lzq.shortlink.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.LocalDateTime;

/**
 * 全局异常处理器。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        FieldError fieldError = exception.getBindingResult().getFieldError();

        String message = fieldError == null
                ? "请求参数不合法"
                : fieldError.getDefaultMessage();

        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleRequestBodyException(
            HttpMessageNotReadableException exception
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "REQUEST_BODY_ERROR",
                "请求体格式错误"
        );
    }

    @ExceptionHandler(ShortLinkNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFoundException(
            ShortLinkNotFoundException exception
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "LINK_NOT_FOUND",
                exception.getMessage()
        );
    }

    @ExceptionHandler(InvalidStatisticsRangeException.class)
    public ResponseEntity<ApiErrorResponse> handleStatisticsRangeException(
            InvalidStatisticsRangeException exception
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_STATISTICS_RANGE",
                exception.getMessage()
        );
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String code,
            String message
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                code,
                message,
                LocalDateTime.now()
        );

        return ResponseEntity.status(status).body(response);
    }
}
