package io.leavesfly.alphaforge.presentation.api;

import com.google.common.util.concurrent.RateLimiter;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API 限流过滤器 — 基于 Guava RateLimiter 的请求限流
 *
 * 限流策略：
 * - 全局限流：20 请求/秒（保护服务器整体）
 * - LLM 密集型 API（/api/chat, /api/analysis）：5 请求/秒（保护 LLM API 配额）
 * - 分析任务 API（/api/analysis/run）：2 请求/秒（防止批量分析滥用）
 *
 * 超过限流时返回 HTTP 429 + JSON 错误信息。
 */
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** 全局限流器 */
    private final RateLimiter globalLimiter = RateLimiter.create(20.0);

    /** 按路径前缀的专用限流器 */
    private final Map<String, RateLimiter> pathLimiters = new ConcurrentHashMap<>();

    public RateLimitFilter() {
        // LLM 密集型 API 限流
        pathLimiters.put("/api/chat", RateLimiter.create(5.0));
        pathLimiters.put("/api/analysis", RateLimiter.create(5.0));
        pathLimiters.put("/api/analysis/run", RateLimiter.create(2.0));
        pathLimiters.put("/api/screening", RateLimiter.create(3.0));
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestURI = httpRequest.getRequestURI();

        // 静态资源和健康检查不限流
        if (isExcluded(requestURI)) {
            chain.doFilter(request, response);
            return;
        }

        // 全局限流检查
        if (!globalLimiter.tryAcquire()) {
            log.warn("全局限流触发: {} {}", httpRequest.getMethod(), requestURI);
            sendTooManyRequests(httpResponse, "请求过于频繁，请稍后重试");
            return;
        }

        // 路径专用限流检查
        RateLimiter pathLimiter = findPathLimiter(requestURI);
        if (pathLimiter != null && !pathLimiter.tryAcquire()) {
            log.warn("路径限流触发: {} {}", httpRequest.getMethod(), requestURI);
            sendTooManyRequests(httpResponse, "AI分析请求过于频繁，请稍后重试");
            return;
        }

        chain.doFilter(request, response);
    }

    /** 判断是否排除限流 */
    private boolean isExcluded(String uri) {
        return uri.startsWith("/web/") ||
                uri.startsWith("/css/") ||
                uri.startsWith("/js/") ||
                uri.equals("/actuator/health") ||
                uri.equals("/actuator/info") ||
                uri.equals("/actuator/prometheus") ||
                uri.equals("/favicon.ico");
    }

    /** 查找匹配的路径限流器 */
    private RateLimiter findPathLimiter(String uri) {
        // 精确匹配优先
        if (pathLimiters.containsKey(uri)) {
            return pathLimiters.get(uri);
        }
        // 前缀匹配
        for (Map.Entry<String, RateLimiter> entry : pathLimiters.entrySet()) {
            if (uri.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** 返回 429 Too Many Requests */
    private void sendTooManyRequests(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":429,\"message\":\"" + message + "\",\"data\":null}");
    }
}
