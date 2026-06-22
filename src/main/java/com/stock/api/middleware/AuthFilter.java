package com.stock.api.middleware;

import com.stock.config.AppConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * JWT认证过滤器
 * 
 * 对应Python版本的 src/auth.py
 * 保护需要认证的API端点
 */
@Component
@Order(1)
public class AuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);
    private final AppConfig config;

    /** 不需要认证的路径 */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/health",
            "/api/v1/auth/login",
            "/api/v1/auth/status"
    );

    public AuthFilter(AppConfig config) {
        this.config = config;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 如果认证未启用，直接放行
        if (!config.isAuthEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String path = httpRequest.getRequestURI();

        // 公开路径放行
        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 非API路径放行(静态资源等)
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        // 验证Token
        String token = extractToken(httpRequest);
        if (token == null || !validateToken(token)) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"未授权，请先登录\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 从请求中提取Token
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        // 也检查cookie
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if ("token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * 验证JWT Token
     */
    private boolean validateToken(String token) {
        try {
            byte[] keyBytes = config.getAuthSecret().getBytes(StandardCharsets.UTF_8);
            // 确保密钥至少32字节
            if (keyBytes.length < 32) {
                byte[] padded = new byte[32];
                System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
                keyBytes = padded;
            }
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(keyBytes))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims != null;
        } catch (Exception e) {
            log.debug("Token验证失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
}
