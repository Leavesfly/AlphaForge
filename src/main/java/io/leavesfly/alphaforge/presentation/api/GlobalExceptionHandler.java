package io.leavesfly.alphaforge.presentation.api;

import io.leavesfly.alphaforge.domain.service.exception.LlmException;
import io.leavesfly.alphaforge.presentation.api.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理，统一 ApiResponse 错误格式。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiResponse.error(400, msg));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, e.getMessage()));
    }

    /** LLM 认证失败 — API Key 无效或权限不足 */
    @ExceptionHandler(LlmException.LlmAuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleLlmAuth(LlmException.LlmAuthException e) {
        log.error("LLM认证失败: model={}", e.getModel(), e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(503, "AI服务认证失败，请检查API Key配置"));
    }

    /** LLM 限流 — 超出供应商配额 */
    @ExceptionHandler(LlmException.LlmRateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleLlmRateLimit(LlmException.LlmRateLimitException e) {
        log.warn("LLM限流: model={}, retryAfterMs={}", e.getModel(), e.getRetryAfterMillis());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(429, "AI服务请求频繁，请稍后重试"));
    }

    /** LLM 超时 */
    @ExceptionHandler(LlmException.LlmTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleLlmTimeout(LlmException.LlmTimeoutException e) {
        log.warn("LLM超时: model={}", e.getModel());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ApiResponse.error(504, "AI服务响应超时，请稍后重试"));
    }

    /** LLM 不可用 — 所有渠道均失败 */
    @ExceptionHandler(LlmException.LlmUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleLlmUnavailable(LlmException.LlmUnavailableException e) {
        log.error("LLM服务不可用", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(503, "AI服务暂时不可用，请稍后重试"));
    }

    /** LLM 响应解析失败 */
    @ExceptionHandler(LlmException.LlmParseException.class)
    public ResponseEntity<ApiResponse<Void>> handleLlmParse(LlmException.LlmParseException e) {
        log.warn("LLM响应解析失败: model={}", e.getModel());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(422, "AI服务返回格式异常"));
    }

    /** LLM 其他异常 */
    @ExceptionHandler(LlmException.class)
    public ResponseEntity<ApiResponse<Void>> handleLlmGeneral(LlmException e) {
        log.error("LLM调用异常: model={}", e.getModel(), e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(503, "AI服务异常，请稍后重试"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "服务器内部错误"));
    }
}
