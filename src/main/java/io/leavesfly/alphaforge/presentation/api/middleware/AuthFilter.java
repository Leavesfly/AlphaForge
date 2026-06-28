package io.leavesfly.alphaforge.presentation.api;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.leavesfly.alphaforge.config.AppConfig;
import io.leavesfly.alphaforge.presentation.api.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT 认证过滤器。AUTH_ENABLED=false 时放行所有请求。
 */
@Component
@Order(1)
public class AuthFilter extends OncePerRequestFilter {

    private static final List<String> WHITELIST_PREFIXES = List.of(
            "/api/v1/health",
            "/api/v1/auth",
            "/bot/",
            "/web/",
            "/static/",
            "/"
    );

    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;

    public AuthFilter(AppConfig appConfig, ObjectMapper objectMapper) {
        this.appConfig = appConfig;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!appConfig.isAuthEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return WHITELIST_PREFIXES.stream().anyMatch(path::startsWith)
                || path.equals("/")
                || path.endsWith(".html")
                || path.endsWith(".js")
                || path.endsWith(".css")
                || path.endsWith(".ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "缺少 Authorization Bearer token");
            return;
        }
        String token = authHeader.substring(7);
        try {
            SecretKey key = Keys.hmacShaKeyFor(
                    appConfig.getAuthSecret().getBytes(StandardCharsets.UTF_8));
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            writeUnauthorized(response, "无效或过期的 token");
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(401, message));
    }
}
