package com.lzq.shortlink.exception;

import com.lzq.shortlink.dto.ApiErrorResponse;
import com.lzq.shortlink.trace.RequestTraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import com.lzq.shortlink.auth.exception.EmailAlreadyRegisteredException;
import com.lzq.shortlink.auth.exception.InvalidCredentialsException;
import com.lzq.shortlink.auth.exception.LoginAttemptRateLimitedException;
import com.lzq.shortlink.auth.exception.LoginRateLimiterUnavailableException;
import com.lzq.shortlink.auth.exception.InvalidRefreshTokenException;
import com.lzq.shortlink.workspace.WorkspaceAccessDeniedException;
import com.lzq.shortlink.workspace.InvalidWorkspaceRoleException;
import com.lzq.shortlink.workspace.WorkspaceMemberAlreadyExistsException;
import com.lzq.shortlink.workspace.WorkspaceMemberNotFoundException;
import com.lzq.shortlink.workspace.WorkspaceMemberOperationDeniedException;
import com.lzq.shortlink.workspace.WorkspaceUserNotFoundException;
import org.springframework.http.HttpHeaders;

import java.time.LocalDateTime;

/**
 * 全局异常处理器。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidationException(
            HandlerMethodValidationException exception
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "请求参数不合法"
        );
    }

    // 处理请求体格式错误异常
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

    // 处理资源不存在异常
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            NoResourceFoundException exception
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "请求资源不存在"
        );
    }

    // 处理响应状态异常
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
            ResponseStatusException exception
    ) {
        HttpStatusCode status = exception.getStatusCode();
        return buildResponse(
                status,
                status.is4xxClientError()
                        ? "REQUEST_REJECTED"
                        : "REQUEST_FAILED",
                "请求无法处理"
        );
    }

    // 处理请求方法不支持异常
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception
    ) {
        ResponseEntity<ApiErrorResponse> response = buildResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "请求方法不支持"
        );
        if (exception.getSupportedHttpMethods() == null
                || exception.getSupportedHttpMethods().isEmpty()) {
            return response;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.putAll(response.getHeaders());
        headers.setAllow(exception.getSupportedHttpMethods());
        return new ResponseEntity<>(
                response.getBody(),
                headers,
                response.getStatusCode()
        );
    }

    // 处理请求内容类型不支持异常
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception
    ) {
        return buildResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "请求内容类型不支持"
        );
    }

    // 处理请求的响应格式不支持异常
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException exception
    ) {
        return buildResponse(
                HttpStatus.NOT_ACCEPTABLE,
                "NOT_ACCEPTABLE",
                "请求的响应格式不支持"
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestParameter(
            MissingServletRequestParameterException exception
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "请求参数不完整"
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
            HttpStatusCode status,
            String code,
            String message
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                code,
                message,
                LocalDateTime.now(),
                RequestTraceContext.currentTraceId()
        );

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailAlreadyRegistered(
            EmailAlreadyRegisteredException exception
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_REGISTERED",
                exception.getMessage()
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException exception
    ) {
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                exception.getMessage()
        );
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRefreshToken(
            InvalidRefreshTokenException exception
    ) {
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "INVALID_REFRESH_TOKEN",
                exception.getMessage()
        );
    }

    @ExceptionHandler(LoginAttemptRateLimitedException.class)
    public ResponseEntity<ApiErrorResponse> handleLoginRateLimited(
            LoginAttemptRateLimitedException exception
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                "LOGIN_RATE_LIMITED",
                exception.getMessage(),
                LocalDateTime.now(),
                RequestTraceContext.currentTraceId()
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(
                        HttpHeaders.RETRY_AFTER,
                        String.valueOf(exception.getRetryAfterSeconds())
                )
                .body(response);
    }

    @ExceptionHandler(LoginRateLimiterUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleLoginRateLimiterUnavailable(
            LoginRateLimiterUnavailableException exception
    ) {
        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "LOGIN_RATE_LIMITER_UNAVAILABLE",
                exception.getMessage()
        );
    }

    @ExceptionHandler(InvalidPaginationException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidPagination(
            InvalidPaginationException exception
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_PAGINATION",
                exception.getMessage()
        );
    }

    @ExceptionHandler(InvalidShortLinkStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidShortLinkStatus(
            InvalidShortLinkStatusException exception
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_LINK_STATUS",
                exception.getMessage()
        );
    }

    @ExceptionHandler(InvalidShortLinkFilterException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidShortLinkFilter(
            InvalidShortLinkFilterException exception
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_LINK_FILTER",
                exception.getMessage()
        );
    }

    @ExceptionHandler(ReservedShortCodeException.class)
    public ResponseEntity<ApiErrorResponse> handleReservedShortCode(
            ReservedShortCodeException exception
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "RESERVED_SHORT_CODE",
                exception.getMessage()
        );
    }

    @ExceptionHandler(ShortCodeAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleShortCodeAlreadyExists(
            ShortCodeAlreadyExistsException exception
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "SHORT_CODE_ALREADY_EXISTS",
                exception.getMessage()
        );
    }

    @ExceptionHandler(WorkspaceAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleWorkspaceAccessDenied(
            WorkspaceAccessDeniedException exception
    ) {
        return buildResponse(
                HttpStatus.FORBIDDEN,
                "WORKSPACE_ACCESS_DENIED",
                exception.getMessage()
        );
    }

    @ExceptionHandler(InvalidWorkspaceRoleException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidWorkspaceRole(
            InvalidWorkspaceRoleException exception
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_WORKSPACE_ROLE",
                exception.getMessage()
        );
    }

    @ExceptionHandler(WorkspaceMemberOperationDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleWorkspaceMemberOperationDenied(
            WorkspaceMemberOperationDeniedException exception
    ) {
        return buildResponse(
                HttpStatus.FORBIDDEN,
                "WORKSPACE_MEMBER_OPERATION_DENIED",
                exception.getMessage()
        );
    }

    @ExceptionHandler(WorkspaceUserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleWorkspaceUserNotFound(
            WorkspaceUserNotFoundException exception
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "WORKSPACE_USER_NOT_FOUND",
                exception.getMessage()
        );
    }

    @ExceptionHandler(WorkspaceMemberNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleWorkspaceMemberNotFound(
            WorkspaceMemberNotFoundException exception
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "WORKSPACE_MEMBER_NOT_FOUND",
                exception.getMessage()
        );
    }

    @ExceptionHandler(WorkspaceMemberAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleWorkspaceMemberAlreadyExists(
            WorkspaceMemberAlreadyExistsException exception
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "WORKSPACE_MEMBER_ALREADY_EXISTS",
                exception.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception
    ) {
        String traceId = RequestTraceContext.currentTraceId();
        LOGGER.error("处理请求时出现未预期异常，traceId={}", traceId, exception);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "系统内部错误，请稍后重试"
        );
    }

    // 处理目标URL格式错误异常
    @ExceptionHandler(InvalidTargetUrlException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTargetUrl(
            InvalidTargetUrlException exception
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_TARGET_URL",
                exception.getMessage()
        );
    }
}
